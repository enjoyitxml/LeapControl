package com.leapcontrol.app.journal

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.leapcontrol.app.DailyMileage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 零记的本地仓库：一个 VIN 一个 JSON 文件（`filesDir/journal/journal_<vin>.json`）。
 *
 * 全部本地、不联网、不参与任何统计上报 —— 花费和行程是最私人不过的数据，
 * 这个 App 又是纯自用，没有任何理由把它传出去。
 */
class JournalStore(context: Context) {

    private val dir = File(context.filesDir, "journal")

    private fun file(vin: String): File {
        val key = vin.ifBlank { "default" }.replace(Regex("""[^A-Za-z0-9_-]"""), "_")
        return File(dir, "journal_$key.json")
    }

    fun load(vin: String): JournalData = runCatching {
        val f = file(vin)
        if (!f.exists()) return@runCatching JournalData.EMPTY
        val text = f.readText()
        if (text.isBlank()) return@runCatching JournalData.EMPTY
        JournalData.fromJson(JSONObject(text))
    }.getOrDefault(JournalData.EMPTY)

    fun save(vin: String, data: JournalData) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            // 先写临时文件再改名：写到一半被杀进程也不会留下半个 JSON，
            // 下次启动读到的要么是旧版本、要么是新版本，不会直接变成空账本。
            val target = file(vin)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(data.toJson().toString())
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }
}

/**
 * 零记的运行时状态。
 *
 * UI 直接读 [data]（Compose State）：改完立刻生效，不等落盘；
 * 落盘放到 IO 协程里做，避免「点保存 → 主线程写文件 → 掉帧」。
 *
 * 数据按 VIN 隔离：换车（`session.selectedVin` 变了）会重新加载，
 * 旧车的数据仍然留在磁盘上，切回去还在。
 */
class JournalRuntime(
    context: Context,
    private val vinProvider: () -> String,
) {
    private val store = JournalStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.applicationContext
        .getSharedPreferences("leapcontrol_journal", Context.MODE_PRIVATE)

    private val _data = mutableStateOf(JournalData.EMPTY)
    val data: State<JournalData> get() = _data

    private var loadedVin: String? = null

    /** 当前数据属于哪个 VIN（UI 用它判断要不要重读）。 */
    val vin: String get() = loadedVin ?: vinProvider()

    /** 确保内存里是「当前这辆车」的数据；VIN 变了就重新读盘。 */
    fun ensureLoaded(): JournalData {
        val vin = vinProvider()
        if (vin != loadedVin) {
            loadedVin = vin
            _data.value = store.load(vin)
        }
        return _data.value
    }

    /**
     * 首次引导是否已经弹过。
     *
     * 「第一次进零记要先设目标」只在**装置第一次**成立：用户如果看完整页又返回了
     * （只想记账、暂时不想定目标），下次再点零记不该又把他按回表单里 ——
     * 概览上那张「定一个用车目标」的引导卡已经足够。
     */
    fun onboardingShown(): Boolean = prefs.getBoolean("onboarding_shown", false)

    fun markOnboardingShown() {
        prefs.edit().putBoolean("onboarding_shown", true).apply()
    }

    /** 从磁盘重新读（外部改过 / 修复用）。 */
    fun reload() {
        loadedVin = vinProvider()
        _data.value = store.load(loadedVin.orEmpty())
    }

    private fun mutate(block: (JournalData) -> JournalData) {
        val vin = vinProvider()
        if (vin != loadedVin) {
            loadedVin = vin
            _data.value = store.load(vin)
        }
        val next = block(_data.value)
        if (next == _data.value) return
        _data.value = next
        val snapshot = next
        scope.launch { store.save(vin, snapshot) }
    }

    // ---------------- 用车目标 ----------------

    fun setGoal(goal: CarGoal) = mutate { it.copy(goal = goal) }

    fun clearGoal() = mutate { it.copy(goal = null) }

    /** 服务端拉到的提车天数（用于自动推算提车日期，也让「相伴天数」更可信）。 */
    fun setOwnershipDays(days: Int?) = mutate {
        val safe = days?.takeIf { d -> d in 1..40 * 365 } ?: return@mutate it
        if (it.ownershipDays == safe) it else it.copy(ownershipDays = safe)
    }

    // ---------------- 用车花费 ----------------

    fun addCost(entry: CostEntry) = mutate { data ->
        data.copy(costs = (data.costs + entry).sortedByDescending { it.date })
    }

    fun deleteCost(id: String) = mutate { data ->
        data.copy(costs = data.costs.filterNot { it.id == id })
    }

    /** 记一笔：金额四舍五入到分，日期归一到当天 0 点。 */
    fun addCost(category: CostCategory, amount: Double, date: Long, note: String = ""): CostEntry? {
        if (!amount.isFinite() || amount <= 0.0) return null
        val entry = CostEntry(
            id = newId(),
            date = JournalDates.startOfDay(date),
            category = category,
            amount = Math.round(amount * 100) / 100.0,
            note = note.trim(),
        )
        addCost(entry)
        return entry
    }

    fun updateCost(entry: CostEntry) = mutate { data ->
        data.copy(costs = data.costs.map { if (it.id == entry.id) entry else it }.sortedByDescending { it.date })
    }

    // ---------------- 城市打卡 ----------------

    /**
     * 打卡一个城市。已经打过就不重复写，返回 false（UI 提示「已经打过卡了」）。
     */
    fun checkin(city: City, date: Long = JournalDates.todayStart(), note: String = ""): Boolean {
        val existed = ensureLoaded().checkins.any { it.id == city.id }
        if (existed) return false
        mutate { data ->
            data.copy(
                checkins = (data.checkins + CityCheckin(
                    id = city.id,
                    city = city.name,
                    province = city.province,
                    date = JournalDates.startOfDay(date),
                    note = note.trim(),
                )).sortedByDescending { it.date },
            )
        }
        return true
    }

    fun removeCheckin(id: String) = mutate { data ->
        data.copy(checkins = data.checkins.filterNot { it.id == id })
    }

    // ---------------- 每日里程 ----------------

    /**
     * 写入某天的行驶里程，**只往上取**：同一天重复记录时取较大的值。
     *
     * 为什么要取大而不是覆盖：服务端近 7 日明细偶尔比本地差分晚一天更新，
     * 先差分到 12km、后服务端说 13km，取 13 才对；反过来先有 13 又收到 12，
     * 说明那一份是旧的，覆盖会越记越少。
     */
    fun recordDailyKm(dayKey: String, km: Double) {
        if (!km.isFinite() || km < 0.0 || km > 5_000.0) return
        val key = JournalDates.parseDayKey(dayKey)?.let { JournalDates.dayKey(it) } ?: dayKey
        mutate { data ->
            val old = data.dailyKm[key] ?: -1.0
            if (km <= old) data else data.copy(dailyKm = data.dailyKm + (key to km))
        }
    }

    /** 合并服务端近 7 日明细（day 格式由接口决定，这里统一归一）。 */
    fun mergeRecentDays(days: List<DailyMileage>) {
        if (days.isEmpty()) return
        mutate { data ->
            val merged = data.dailyKm.toMutableMap()
            var changed = false
            days.forEach { d ->
                val key = JournalDates.parseDayKey(d.day)?.let { JournalDates.dayKey(it) } ?: return@forEach
                if (!d.km.isFinite() || d.km < 0.0 || d.km > 5_000.0) return@forEach
                val old = merged[key]
                if (old == null || d.km > old) {
                    merged[key] = d.km
                    changed = true
                }
            }
            if (!changed) data else data.copy(dailyKm = merged)
        }
    }

    /**
     * 用「车况总里程」做日差分，补上今天的行驶里程。
     *
     * 同一天第一次调用只记下起点（当天里程仍是 0，等下一次刷新才有增量）；
     * 跨天之后自动换基线。里程回退（换了仪表 / 数据异常）不写负值。
     */
    fun recordOdometer(totalKm: Double?) {
        val total = totalKm?.takeIf { it.isFinite() && it >= 0.0 && it < 2_000_000.0 } ?: return
        val today = JournalDates.dayKey(JournalDates.todayStart())
        mutate { data ->
            val baseDay = data.odometerDay
            val baseKm = data.odometerKm
            if (baseDay != today || baseKm == null) {
                return@mutate data.copy(odometerDay = today, odometerKm = total)
            }
            val delta = total - baseKm
            if (delta <= 0.0 || delta > 2_000.0) return@mutate data
            val todayKm = (data.dailyKm[today] ?: 0.0).coerceAtLeast(delta)
            data.copy(
                dailyKm = data.dailyKm + (today to todayKm),
                odometerKm = total,
            )
        }
    }

    /** 近 [days] 天的里程（含今天），按日期升序；缺的天补 0。 */
    fun recentKm(days: Int = 14, today: java.time.LocalDate = JournalDates.today()): List<DailyKm> {
        val data = ensureLoaded()
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val key = JournalDates.dayKey(date)
            DailyKm(key, date, data.dailyKm[key] ?: 0.0)
        }
    }

    fun totalKm(): Double = ensureLoaded().dailyKm.values.fold(0.0) { acc, v -> acc + v }

    private fun newId(): String = UUID.randomUUID().toString().take(8)
}

/** 打卡页柱状图用的一天。 */
data class DailyKm(
    val key: String,
    val date: java.time.LocalDate,
    val km: Double,
)
