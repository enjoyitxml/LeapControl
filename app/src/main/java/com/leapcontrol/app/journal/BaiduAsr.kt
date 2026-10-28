package com.leapcontrol.app.journal

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 一次识别请求的结果。[text] 与 [error] 只会有一个非空。
 *
 * [authFailed] 单独标出来，是为了能在 token 失效时静默换一次 token 重试，
 * 而不是把「凭据过期」这种偶发问题直接甩给用户。
 */
data class AsrReply(
    val text: String? = null,
    val error: String? = null,
    val authFailed: Boolean = false,
)

/** 凭据 / 网络类错误，[message] 直接就是能给用户看的中文。 */
private class AsrException(message: String) : Exception(message)

/**
 * 百度短语音识别（REST）。
 *
 * 为什么不用系统 `SpeechRecognizer`：那需要设备上存在一个可供第三方绑定的
 * `android.speech.RecognitionService`，国产 ROM 多数没有，或有也不导出 ——
 * `bindService` 被拒后立刻回调 `ERROR_CLIENT`（或被看门狗判成「服务没响应」），
 * 重试多少次都一样。
 *
 * 这里改成「App 自己录音（[com.leapcontrol.app.ui.VoiceRecorder]）+ 上传音频换文字」，
 * 录音节奏、声量、界面完全自己掌控，只把最后一步转写交给云端。
 *
 * 接口要点：
 *  - 先用 API Key / Secret Key 换 access_token（有效期 30 天，这里缓存 25 天）；
 *  - 识别请求把 pcm 以 base64 放进 JSON body，`len` 是**原始字节数**；
 *  - 单条音频上限 60 秒，采样率 16000、16bit、单声道。
 */
object BaiduAsr {

    private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    private const val ASR_URL = "https://vop.baidu.com/server_api"

    const val SAMPLE_RATE = 16_000

    /** 百度侧单条音频上限 60 秒，录音到 55 秒就收，别把错误留给服务端。 */
    const val MAX_SECONDS = 55

    /** 1537 = 普通话（纯中文识别），1536 = 普通话带标点，这里要干净的一句话用 1537。 */
    private const val DEV_PID = 1537

    private const val TOKEN_TTL_MS = 25L * 24 * 60 * 60 * 1000

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedTokenAt = 0L

    @Volatile
    private var cachedTokenOwner = ""

    /**
     * 识别一段 16k/16bit/单声道 PCM。
     *
     * 网络与解析错误都折成 [AsrReply.error] 里的中文，调用方不需要判断异常类型。
     */
    suspend fun recognize(pcm: ByteArray, apiKey: String, secretKey: String): AsrReply =
        withContext(Dispatchers.IO) {
            if (pcm.size < SAMPLE_RATE / 2) {
                return@withContext AsrReply(error = "太短了，再说一遍吧")
            }
            if (pcm.size > SAMPLE_RATE * 2 * MAX_SECONDS) {
                return@withContext AsrReply(error = "说得太长了，分两次记吧")
            }
            val token = try {
                fetchToken(apiKey.trim(), secretKey.trim())
            } catch (e: AsrException) {
                return@withContext AsrReply(error = e.message.orEmpty(), authFailed = true)
            } catch (e: Exception) {
                return@withContext AsrReply(error = "拿不到识别凭据：${e.message ?: "未知错误"}", authFailed = true)
            }
            var reply = request(pcm, token)
            // token 过期时换一个再打一次，别让用户去设置里手动重填
            if (reply.authFailed) {
                cachedToken = null
                val fresh = runCatching { fetchToken(apiKey.trim(), secretKey.trim()) }.getOrNull()
                if (fresh != null && fresh != token) reply = request(pcm, fresh)
            }
            reply
        }

    /**
     * 只校验 Key 能不能换到 token，不发音频。
     *
     * 设置页、语音浮层的「填 Key」都用它当场给回执 —— 之前是「保存了但不知道对不对」，
     * 结果一路走到录音结束才报错，很难定位。
     *
     * @return null 表示凭据有效；否则是可以直接显示的中文原因。
     */
    suspend fun checkCredentials(apiKey: String, secretKey: String): String? =
        withContext(Dispatchers.IO) {
            val api = apiKey.trim()
            val secret = secretKey.trim()
            if (api.isEmpty() || secret.isEmpty()) return@withContext "两个 Key 都要填"
            try {
                fetchToken(api, secret)
                null
            } catch (e: AsrException) {
                e.message.orEmpty()
            } catch (e: Exception) {
                "校验失败：${e.message ?: "未知错误"}"
            }
        }

    /** 丢掉缓存的 token（改 Key 之后要调，否则会拿着旧凭据去打）。 */
    fun forgetToken() {
        cachedToken = null
        cachedTokenAt = 0L
        cachedTokenOwner = ""
    }

    // ---------------- 内部实现 ----------------

    private fun fetchToken(apiKey: String, secretKey: String): String {
        if (apiKey.isEmpty() || secretKey.isEmpty()) throw AsrException("两个 Key 都要填")
        val owner = "$apiKey|${secretKey.hashCode()}"
        val fresh = cachedToken
        if (fresh != null && owner == cachedTokenOwner && System.currentTimeMillis() - cachedTokenAt < TOKEN_TTL_MS) {
            return fresh
        }
        val url = buildString {
            append(TOKEN_URL)
            append("?grant_type=client_credentials")
            append("&client_id=").append(URLEncoder.encode(apiKey, "UTF-8"))
            append("&client_secret=").append(URLEncoder.encode(secretKey, "UTF-8"))
        }
        // 用 GET：POST 空 body 时部分网关会因为缺 Content-Length 直接 400，GET 没有这个变量
        val raw = try {
            httpGet(url)
        } catch (e: Exception) {
            throw AsrException("连不上百度（网络问题），换个网络再试")
        }
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw AsrException("百度返回了看不懂的内容，稍后再试")
        }
        val token = json.optString("access_token")
        if (token.isBlank()) {
            val desc = json.optString("error_description").ifBlank {
                json.optString("error").ifBlank { "未知原因" }
            }
            throw AsrException("Key 不对，或应用没开通「短语音识别」：$desc")
        }
        cachedToken = token
        cachedTokenAt = System.currentTimeMillis()
        cachedTokenOwner = owner
        return token
    }

    private fun request(pcm: ByteArray, token: String): AsrReply {
        val body = JSONObject().apply {
            put("format", "pcm")
            put("rate", SAMPLE_RATE)
            put("channel", 1)
            put("dev_pid", DEV_PID)
            put("cuid", "leapcontrol")
            put("token", token)
            put("len", pcm.size)
            put("speech", Base64.encodeToString(pcm, Base64.NO_WRAP))
        }.toString()
        val raw = try {
            httpPost(ASR_URL, body)
        } catch (e: Exception) {
            return AsrReply(error = "连不上百度语音（网络问题），检查网络后重试")
        }
        return runCatching { parseBaiduReply(raw) }
            .getOrElse { AsrReply(error = "识别服务返回了看不懂的内容") }
    }

    private fun httpGet(url: String): String = open(url) { conn ->
        conn.requestMethod = "GET"
    }

    private fun httpPost(url: String, body: String): String = open(url) { conn ->
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val bytes = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
    }

    private fun open(url: String, prepare: (HttpURLConnection) -> Unit): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        try {
            prepare(conn)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // 鉴权失败时百度会返回 4xx + JSON（带 error / error_description），把 body 带出去更好定位
            if (code !in 200..299 && text.isBlank()) throw AsrException("HTTP $code")
            return text
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * 解析 `vop.baidu.com/server_api` 的返回。
 *
 * 抽成纯函数是为了能在 JVM 单测里直接跑（不碰 Base64 / 网络）。
 */
internal fun parseBaiduReply(raw: String): AsrReply {
    val json = runCatching { JSONObject(raw) }.getOrNull()
        ?: return AsrReply(error = "识别服务返回了看不懂的内容")
    val errNo = json.optInt("err_no", -1)
    if (errNo == 0) {
        val text = json.optJSONArray("result")
            ?.optString(0)
            .orEmpty()
            .replace("\n", "")
            .trim()
        return if (text.isBlank()) AsrReply(error = "没听清，再说一次吧") else AsrReply(text = text)
    }
    return AsrReply(
        error = baiduAsrErrorText(errNo),
        authFailed = errNo == 110 || errNo == 111 || errNo == 3302,
    )
}

/** 百度语音错误码翻成人话；未知码把数字带上，方便回来查。 */
internal fun baiduAsrErrorText(errNo: Int): String = when (errNo) {
    3300 -> "录音格式不对，重新说一次试试"
    3301 -> "音频质量太差，靠近点再说一次"
    110, 111, 3302 -> "识别凭据失效了，去设置里检查百度 Key"
    3303 -> "百度语音服务暂时不可用，稍后再试"
    3304 -> "免费额度用完了，或者请求太频繁"
    3305 -> "音频太长或参数不对，说短一点"
    3307 -> "音频超过 60 秒了，分两次说吧"
    3308, 3309 -> "这段音频识别不了，换个说法试试"
    3310, 3311, 3312 -> "没听到声音，再按一次说话"
    else -> "识别失败（错误码 $errNo）"
}
