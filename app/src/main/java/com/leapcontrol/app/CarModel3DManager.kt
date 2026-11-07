package com.leapcontrol.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 官方 3D 车模包的本地缓存、参数缓存与开关。
 *
 * 官方 3D 车模不是一个文件，而是**两个 zip 包**：
 *  - `h5Key` 包：H5 运行时 + three.js 引擎 + `index.html`，负责把车画出来；
 *  - `srcKey` 包：这台车的三维网格、材质贴图、轮毂等资产。
 *
 * 两个包必须解到**同一个目录**：H5 里按相对路径找资产，分开放就只剩一个空壳。
 * 解压顺序也固定 —— 先 H5 再资产，否则先到的资产会被后来的 H5 覆盖标记影响。
 *
 * 为什么放 `filesDir` 而不是 `cacheDir`：整包约 12MB，cacheDir 随时会被系统清掉，
 * 重下一次既费流量又费时间。清理只在用户主动点「清除 3D 车模」时发生。
 *
 * 关于「颜色/配置和官方不一致」：2D 定妆照是服务端按车辆档案**预先渲染**的一张图，
 * 客户端没有任何入参；3D 是按 [CarModel3DMeta.modelParam]（车型 / 年款 / 车漆色号 /
 * 车顶色 / 座椅）在设备上实时渲染，所以 3D 这条路才对得上实车。
 */
object CarModel3DManager {

    /** 模型包目录名（`filesDir` 下）。 */
    private const val DIR_NAME = "car_model_3d"

    private const val PREFS = "car_model_3d"

    /** 就绪打标文件：H5 包解压完写 `.ready`，资产包写 `.ready_<srcKey>`。 */
    private const val MARK_READY = ".ready"
    private const val MARK_SRC_PREFIX = ".ready_"

    /** 「这台车没有 3D 包」这个结论的有效期：一天。 */
    private const val UNAVAILABLE_TTL_MS = 24 * 60 * 60 * 1000L

    // ------------------------------------------------------------------ 目录

    fun baseDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!isDirectory) mkdirs() }

    /**
     * 把 key 里的非字母数字换成下划线：key 来自服务端，可能带 `-` `/` `:`，
     * 直接当目录名用会有路径穿越与意外的层级。
     */
    fun sanitizeKey(key: String): String =
        key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    fun modelDir(baseDir: File, key: String): File = File(baseDir, sanitizeKey(key))

    fun modelDir(context: Context, key: String): File = modelDir(baseDir(context), key)

    /** 整包是否已经就位（H5 有 index.html + 资产包打过标）。 */
    fun isReady(baseDir: File, h5Key: String?, srcKey: String? = null): Boolean {
        if (h5Key.isNullOrBlank()) return false
        val dir = modelDir(baseDir, h5Key)
        val index = File(dir, "index.html")
        if (!File(dir, MARK_READY).exists()) return false
        if (!index.isFile || index.length() <= 0L) return false
        if (!srcKey.isNullOrBlank() && !File(dir, MARK_SRC_PREFIX + sanitizeKey(srcKey)).exists()) {
            return false
        }
        return true
    }

    fun isReady(context: Context, h5Key: String?, srcKey: String? = null): Boolean =
        isReady(baseDir(context), h5Key, srcKey)

    /**
     * 本地已经就绪的 H5 包 key。
     *
     * 用在「元数据还没拉到、但上次已经下载过」的场景：先按这个 key 把 3D 显示出来，
     * 不必等网络往返 —— 冷启动进首页那一下最明显。
     */
    fun firstReadyKey(context: Context): String? {
        val dirs = baseDir(context).listFiles() ?: return null
        return dirs.firstOrNull {
            it.isDirectory &&
                File(it, MARK_READY).exists() &&
                File(it, "index.html").let { f -> f.isFile && f.length() > 0L }
        }?.name
    }

    /** 删掉某个模型包（换车型 / 用户点清理时用）。 */
    fun clearPackage(context: Context, h5Key: String?) {
        if (h5Key.isNullOrBlank()) return
        runCatching { modelDir(context, h5Key).deleteRecursively() }
    }

    /**
     * 把两个包下下来并解到同一个目录。
     *
     * [download] 由调用方注入（`LeapmotorApi.downloadCarModelPackage`）—— 这里只管
     * 「谁先下、下到哪、什么时候算好」，不碰网络实现，也就能单独测。
     *
     * 顺序固定：先 H5 运行时、再车型资产。反过来的话，资产包先落盘时目录里还没有
     * `index.html`，中途被系统杀掉就变成一个「有资产没页面」的半成品 —— 这种目录
     * 既不能显示，也不会被判成未就绪，下次进来还会继续用。
     */
    fun syncPackage(
        baseDir: File,
        h5Key: String,
        srcKey: String?,
        download: (key: String, destDir: File) -> Boolean,
    ): Boolean {
        if (h5Key.isBlank()) return false
        if (isReady(baseDir, h5Key, srcKey)) return true

        val dir = modelDir(baseDir, h5Key)
        dir.mkdirs()

        val index = File(dir, "index.html")
        val h5Ready = File(dir, MARK_READY).exists() && index.isFile && index.length() > 0L
        if (!h5Ready) {
            if (!download(h5Key, dir)) return false
            if (!index.isFile || index.length() <= 0L) return false
            runCatching { File(dir, MARK_READY).createNewFile() }
        }

        if (!srcKey.isNullOrBlank()) {
            val marker = File(dir, MARK_SRC_PREFIX + sanitizeKey(srcKey))
            if (!marker.exists()) {
                if (!download(srcKey, dir)) return false
                runCatching { marker.createNewFile() }
            }
        }
        return isReady(baseDir, h5Key, srcKey)
    }

    fun syncPackage(
        context: Context,
        h5Key: String,
        srcKey: String?,
        download: (key: String, destDir: File) -> Boolean,
    ): Boolean = syncPackage(baseDir(context), h5Key, srcKey, download)

    /** 清掉所有 3D 资源，但保留开关（下次进首页会自动重下）。 */
    fun clearAll(context: Context) {
        runCatching { baseDir(context).deleteRecursively() }
        runCatching { baseDir(context).mkdirs() }
    }

    /** 整个 3D 目录占了多少字节（设置页显示用）。 */
    fun sizeOnDisk(context: Context): Long {
        val root = baseDir(context)
        if (!root.isDirectory) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // ------------------------------------------------------------------ 元数据

    /**
     * 官方下发的 3D 渲染参数。
     *
     * [modelParam] 是原封不动的 JSON —— 官方 H5 的 `newInit()` 要的就是它的字段名，
     * 我们不在中间做映射，少一层就可能少一个官方新加的字段（比如某个车顶配色）。
     */
    data class Meta(
        val h5Key: String,
        val srcKey: String?,
        val modelParam: JSONObject?,
        val pictureKey: String = "",
    )

    fun saveMeta(context: Context, vin: String, meta: Meta) {
        if (vin.isBlank() || meta.h5Key.isBlank()) return
        val json = JSONObject().apply {
            put("h5Key", meta.h5Key)
            put("srcKey", meta.srcKey.orEmpty())
            put("pictureKey", meta.pictureKey)
            if (meta.modelParam != null) put("modelParam", meta.modelParam)
        }
        prefs(context).edit().putString("meta_$vin", json.toString()).apply()
    }

    /** 读缓存的元数据；没有或坏了返回 null（调用方按「需联网拉一次」处理）。 */
    fun loadMeta(context: Context, vin: String): Meta? {
        if (vin.isBlank()) return null
        val raw = prefs(context).getString("meta_$vin", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val h5Key = json.optString("h5Key").takeIf { it.isNotBlank() } ?: return null
            Meta(
                h5Key = h5Key,
                srcKey = json.optString("srcKey").takeIf { it.isNotBlank() },
                modelParam = json.optJSONObject("modelParam"),
                pictureKey = json.optString("pictureKey"),
            )
        }.getOrNull()
    }

    fun clearMeta(context: Context, vin: String) {
        if (vin.isBlank()) return
        prefs(context).edit().remove("meta_$vin").apply()
    }

    /**
     * 「这台车官方没有 3D 包」这个结论要不要跳过联网。
     *
     * 每次进首页都去问一遍的话，对一个没有 3D 的车型就是每次都把
     * `carpicture` 的几个候选路径走满 —— 白费流量和时间。记 24 小时，
     * 既不至于让偶发失败把结论钉死（上游踩过这个坑），也不用天天重问。
     */
    fun shouldSkipUnavailable(
        context: Context,
        vin: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (vin.isBlank()) return false
        val at = prefs(context).getLong("unavailable_$vin", 0L)
        return at > 0L && now - at < UNAVAILABLE_TTL_MS
    }

    fun markUnavailable(
        context: Context,
        vin: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (vin.isBlank()) return
        prefs(context).edit().putLong("unavailable_$vin", now).apply()
    }

    fun clearUnavailable(context: Context, vin: String) {
        if (vin.isBlank()) return
        prefs(context).edit().remove("unavailable_$vin").apply()
    }

    // ------------------------------------------------------------------ 开关

    /** 这台车要不要用 3D 车模。默认开 —— 关掉是少数派（老机型 / 省电）。 */
    fun isEnabled(context: Context, vin: String): Boolean =
        prefs(context).getBoolean("enabled_${vin.ifBlank { "default" }}", true)

    fun setEnabled(context: Context, vin: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled_${vin.ifBlank { "default" }}", enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
