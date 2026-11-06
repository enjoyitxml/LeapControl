package com.leapcontrol.app.trip

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 行程数据的落盘：一个 `filesDir/trip/trips.json`，按 VIN 分车。
 *
 * 只用 App 私有目录，不上传、不进日志。写入走「新文件 → 旧文件改名备份 → 换名」，
 * 任何一步失败都保留上一份完整数据（和零记 journal.json 同一套思路）。
 */
object TripStore {
    private const val DIR = "trip"
    private const val NAME = "trips.json"
    private const val SCHEMA = 1

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR)

    private fun file(context: Context): File = File(dir(context), NAME)

    private fun readRoot(context: Context): JSONObject {
        val target = file(context)
        if (!target.exists()) return JSONObject()
        return runCatching { JSONObject(target.readText()) }.getOrDefault(JSONObject())
    }

    @Synchronized
    fun load(context: Context, vin: String): TripOverview {
        if (vin.isBlank()) return TripOverview()
        val cars = readRoot(context).optJSONObject("cars") ?: return TripOverview(vin)
        return TripOverview.fromJson(vin, cars.optJSONObject(vin))
    }

    @Synchronized
    fun save(context: Context, state: TripOverview) {
        if (state.vin.isBlank()) return
        val folder = dir(context)
        if (!folder.exists() && !folder.mkdirs()) return
        val root = readRoot(context)
        val cars = root.optJSONObject("cars") ?: JSONObject().also { root.put("cars", it) }
        cars.put(state.vin, state.toJson())
        root.put("schema", SCHEMA)

        val target = file(context)
        val tmp = File(folder, "$NAME.new")
        val backup = File(folder, "$NAME.bak")
        try {
            tmp.writeText(root.toString())
            if (target.exists() && !target.renameTo(backup)) {
                tmp.delete()
                return
            }
            if (tmp.renameTo(target)) {
                backup.delete()
            } else {
                backup.renameTo(target)
                tmp.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    /** 退出登录时整份清掉：下一位车主不该看到上一位的行踪。 */
    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { File(dir(context), "$NAME.bak").delete() }
        runCatching { File(dir(context), "$NAME.new").delete() }
    }
}
