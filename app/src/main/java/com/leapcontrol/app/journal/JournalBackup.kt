package com.leapcontrol.app.journal

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 零记的备份文件：把本机**所有车辆**的零记数据打成一个 JSON，用来换手机时搬数据。
 *
 * 零记的账全在 `filesDir/journal/` 里，卸载重装、换机器都会跟着没 —— 这是本地存储
 * 换来的代价，所以必须给一个能自己把数据带走的出口。文件走系统的「保存到…」/「打开文件」，
 * 不经过任何服务器，也就不存在「备份被传到哪去了」这个问题。
 *
 * 文件长这样（[FORMAT] = 1）：
 * ```
 * { "kind": "leapcontrol-journal", "format": 1, "appVersion": "0.2.52",
 *   "exportedAt": 1761..., "cardOrder": "...",
 *   "vehicles": [ { "vin": "L...", "data": { ... JournalData.toJson() ... } } ] }
 * ```
 * `kind` 是「这是谁的备份」的签名：随便拿一个 JSON 进来能立刻被认出来，
 * 而不是解析到一半才发现字段对不上。
 */
object JournalBackup {

    const val KIND = "leapcontrol-journal"

    /** 当前格式版本。导入放行 `format <= FORMAT`，以后加字段不必升版本。 */
    const val FORMAT = 1

    /** 一辆车的一份数据（[vin] 是文件名的片段，与 [JournalStore] 的命名规则一致）。 */
    data class Vehicle(val vin: String, val data: JournalData)

    /** 一份备份文件的全部内容。 */
    data class Payload(
        val exportedAt: Long,
        val appVersion: String,
        val vehicles: List<Vehicle>,
        /** 概览页卡片排版；没改过就是 null。 */
        val cardOrder: String? = null,
    ) {
        val vehicleCount: Int get() = vehicles.size
        val costCount: Int get() = vehicles.sumOf { it.data.costs.size }
        val checkinCount: Int get() = vehicles.sumOf { it.data.checkins.size }
        val wishCount: Int get() = vehicles.sumOf { it.data.wishes.size }
        val kmDayCount: Int get() = vehicles.sumOf { it.data.dailyKm.size }
    }

    /** 一次导入实际新增了多少（备份里本机已有的部分不算，否则数字会虚高）。 */
    data class Summary(
        val vehicles: Int = 0,
        val newCosts: Int = 0,
        val newCheckins: Int = 0,
        val newWishes: Int = 0,
        val newKmDays: Int = 0,
        val cardOrderRestored: Boolean = false,
    )

    /** 文件读不懂时抛它：`message` 是给用户看的原话，可以直接弹在界面上。 */
    class BackupException(message: String) : Exception(message)

    // ---------------- 读写 ----------------

    fun encode(payload: Payload): String {
        val root = JSONObject()
        root.put("kind", KIND)
        root.put("format", FORMAT)
        root.put("app", "零控")
        root.put("appVersion", payload.appVersion)
        root.put("exportedAt", payload.exportedAt)
        payload.cardOrder?.takeIf { it.isNotBlank() }?.let { root.put("cardOrder", it) }
        root.put(
            "vehicles",
            JSONArray().apply {
                payload.vehicles.forEach { vehicle ->
                    put(
                        JSONObject().apply {
                            put("vin", vehicle.vin)
                            put("data", vehicle.data.toJson())
                        },
                    )
                }
            },
        )
        // 缩进两格的纯文本：用户拿文本编辑器打开也能看懂，出问题时好判断
        return root.toString(2)
    }

    fun decode(text: String): Result<Payload> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(BackupException("这个文件是空的"))
        val root = runCatching { JSONObject(trimmed) }.getOrElse {
            return Result.failure(BackupException("这不是零控的备份文件"))
        }
        if (root.optString("kind") != KIND) {
            return Result.failure(BackupException("这不是零控的备份文件"))
        }
        val format = root.optInt("format", 0)
        if (format <= 0 || format > FORMAT) {
            return Result.failure(
                BackupException("备份文件的版本（$format）比当前 App 新，先把零控更新到最新版"),
            )
        }
        val array = root.optJSONArray("vehicles")
        if (array == null || array.length() == 0) {
            return Result.failure(BackupException("备份里没有零记数据"))
        }
        val vehicles = ArrayList<Vehicle>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val vin = item.optString("vin").trim()
                .ifBlank { "default" }
                .take(64)
            val data = JournalData.fromJson(item.optJSONObject("data"))
            vehicles += Vehicle(vin, data)
        }
        if (vehicles.isEmpty()) return Result.failure(BackupException("备份里没有可用的零记数据"))
        return Result.success(
            Payload(
                exportedAt = root.opt("exportedAt")?.toString()?.toLongOrNull() ?: 0L,
                appVersion = root.optString("appVersion").trim(),
                vehicles = vehicles,
                cardOrder = root.optString("cardOrder").takeIf { it.isNotBlank() },
            ),
        )
    }

    // ---------------- 合并 ----------------

    /**
     * 把备份合并进本机数据，**不删任何本机记录**。
     *
     * 为什么是合并而不是覆盖：新手机上本机是空的，合并就等于完整恢复；
     * 而如果用户选错文件、或把旧备份导回已经用了很久的手机，覆盖会直接把新记的账冲掉，
     * 这种操作没法撤销。合并最坏的情况只是「没变化」。
     *
     * 规则：
     *  - 流水 / 打卡 / 心愿按 id 去重 —— 同一笔账不会变成两条；
     *  - 每日里程取较大值 —— 与服务端明细、总里程差分的口径一致（见 JournalStore）；
     *  - 目标、保养、保险这类「单值」本机有就保留本机（可能是后来改过的），没有才用备份的。
     */
    fun merge(local: JournalData, incoming: JournalData): JournalData {
        val daily = local.dailyKm.toMutableMap()
        incoming.dailyKm.forEach { (key, km) ->
            if (km > (daily[key] ?: -1.0)) daily[key] = km
        }
        return local.copy(
            goal = local.goal ?: incoming.goal,
            ownershipDays = local.ownershipDays ?: incoming.ownershipDays,
            maintenance = local.maintenance ?: incoming.maintenance,
            insurance = local.insurance ?: incoming.insurance,
            odometerKm = local.odometerKm ?: incoming.odometerKm,
            odometerDay = local.odometerDay ?: incoming.odometerDay,
            costs = unionById(local.costs, incoming.costs) { it.id }.sortedByDescending { it.date },
            checkins = unionById(local.checkins, incoming.checkins) { it.id }.sortedByDescending { it.date },
            wishes = unionById(local.wishes, incoming.wishes) { it.id }.sortedBy { it.added },
            dailyKm = daily,
        )
    }

    private fun <T> unionById(local: List<T>, incoming: List<T>, id: (T) -> String): List<T> {
        val seen = local.mapTo(HashSet()) { id(it) }
        return local + incoming.filter { seen.add(id(it)) }
    }

    // ---------------- 文案 ----------------

    /** 默认文件名：`零控零记-20261030-1930.json`（中文名在文件管理器里一眼能认出来）。 */
    fun fileName(now: Long = System.currentTimeMillis()): String {
        val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        return "零控零记-" + time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US)) + ".json"
    }

    /** 导入确认框里的正文：让用户先看清这份备份是什么、有多少东西。 */
    fun describe(payload: Payload): String = buildString {
        append("导出时间：")
        append(timeLabel(payload.exportedAt))
        append('\n')
        append("车辆：").append(payload.vehicleCount).append(" 辆")
        // 汇总而不是逐车罗列：几辆车的数据加起来才是「这次能恢复多少」
        val plans = payload.vehicles.sumOf { v ->
            (if (v.data.goal != null) 1 else 0) +
                (if (v.data.maintenance != null) 1 else 0) +
                (if (v.data.insurance != null) 1 else 0)
        }
        val parts = buildList {
            if (payload.costCount > 0) add("记账 ${payload.costCount} 笔")
            if (payload.checkinCount > 0) add("打卡 ${payload.checkinCount} 城")
            if (payload.wishCount > 0) add("心愿 ${payload.wishCount} 个")
            if (payload.kmDayCount > 0) add("里程 ${payload.kmDayCount} 天")
            if (plans > 0) add("目标与计划 $plans 项")
        }
        if (parts.isNotEmpty()) {
            append('\n')
            append(parts.joinToString(" · "))
        }
    }

    /** 导入结果：只报真正新增的部分。 */
    fun describe(summary: Summary): String {
        val parts = buildList {
            if (summary.newCosts > 0) add("记账 ${summary.newCosts} 笔")
            if (summary.newCheckins > 0) add("打卡 ${summary.newCheckins} 城")
            if (summary.newWishes > 0) add("心愿 ${summary.newWishes} 个")
            if (summary.newKmDays > 0) add("里程 ${summary.newKmDays} 天")
            if (summary.cardOrderRestored) add("卡片排版")
        }
        if (parts.isEmpty()) return "备份里的记录本机都已经有了，没有需要新增的内容"
        return "已导入：" + parts.joinToString("、")
    }

    private fun timeLabel(millis: Long): String {
        if (millis <= 0L) return "未知"
        val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return time.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA))
    }
}
