package com.leapcontrol.app.ui

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.leapcontrol.app.journal.BaiduAsr
import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 录音器：16kHz / 16bit / 单声道 PCM，边录边把音量抛给界面。
 *
 * 为什么不复用系统语音识别：它把录音封在识别服务里，App 既拿不到音频也控制不了节奏，
 * 而国产 ROM 多数不开放那个服务。自己录就没有这层依赖，声量也直接来自采样，
 * 比 `onRmsChanged` 更跟手。
 *
 * 采集源优先 `VOICE_RECOGNITION`（系统会做降噪与回声消除，适合识别），
 * 失败再退回普通 `MIC`。
 */
class VoiceRecorder(
    private val onLevel: (Float) -> Unit,
    /** 录到上限（[BaiduAsr.MAX_SECONDS]）自动收尾，交给界面决定发不发。 */
    private val onLimitReached: () -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private val buffer = ByteArrayOutputStream()
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    val sampleRate: Int = BaiduAsr.SAMPLE_RATE

    /** 开始录音；返回 false 表示麦克风不可用（多半是被别的应用占着或没有权限）。 */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return false
        val min = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return false
        val rec = open(min) ?: return false
        buffer.reset()
        record = rec
        running = true
        thread = Thread({ loop(rec, maxOf(min, 4096)) }, "leap-voice").apply { start() }
        return true
    }

    /** 停止录音并取回 PCM；没录到东西就是空数组。 */
    fun stop(): ByteArray {
        running = false
        val worker = thread
        thread = null
        runCatching { worker?.join(500) }
        val rec = record
        record = null
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        main.post { onLevel(0f) }
        return buffer.toByteArray()
    }

    @SuppressLint("MissingPermission")
    private fun open(minBuffer: Int): AudioRecord? {
        val size = maxOf(minBuffer * 2, 8192)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val rec = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size,
                )
            }.getOrNull() ?: continue
            if (rec.state == AudioRecord.STATE_INITIALIZED && runCatching { rec.startRecording() }.isSuccess) {
                return rec
            }
            runCatching { rec.release() }
        }
        return null
    }

    private fun loop(rec: AudioRecord, chunkSize: Int) {
        val chunk = ByteArray(chunkSize)
        val limitBytes = sampleRate * 2 * BaiduAsr.MAX_SECONDS
        var total = 0
        while (running) {
            val read = runCatching { rec.read(chunk, 0, chunk.size) }.getOrDefault(-1)
            if (read < 0) break
            if (read == 0) continue
            buffer.write(chunk, 0, read)
            total += read
            val level = levelOf(chunk, read)
            main.post { onLevel(level) }
            if (total >= limitBytes) {
                running = false
                main.post { onLimitReached() }
                break
            }
        }
    }

    /** 16bit PCM 的 RMS → 0~1 的显示音量（-52dB 以下算静音，-10dB 以上拉满）。 */
    private fun levelOf(data: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = (data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample
            i += 2
        }
        val count = length / 2
        if (count <= 0) return 0f
        val rms = sqrt(sum / count) / 32768.0
        if (rms < 1e-4) return 0f
        val db = 20 * log10(rms)
        return ((db + 52.0) / 42.0).coerceIn(0.0, 1.0).toFloat()
    }
}
