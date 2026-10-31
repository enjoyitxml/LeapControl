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

    /** VIN → 文件名片段。备份/迁移要按同一套规则认文件，所以单独暴露出来。 */
    fun keyOf(vin: String): String =
        vin.ifBlank { "default" }.replace(Regex("""[^A-Za-z0-9_-]"""), "_")

    private fun file(vin: String): File = File(dir, "$FILE_PREFIX${keyOf(vin)}$FILE_SUFFIX")

    /**
     * 读出所有车辆的数据（key 是文件名里的 VIN 片段）。
     *
     * 换过车的人磁盘上会同时留着几份，导出时全都要带上 —— 只说「导出当前车」的话，
     * 另一辆车的账就永远搬不走了。读不动的单个文件跳过，不影响其余部分。
     */
    fun loadAll(): Map<String, JournalData> {
        if (!dir.exists()) return emptyMap()
        val files = dir.listFiles() ?: return emptyMap()
        val out = LinkedHashMap<String, JournalData>()
        files.forEach { f ->
            val name = f.name
            if (!f.isFile || !name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return@forEach
            val key = name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
            if (key.isBlank()) return@forEach
            out[key] = runCatching {
                JournalData.fromJson(JSONObject(f.readText()))
            }.getOrDefault(JournalData.EMPTY)
        }
        return out
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

    /**
     * 概览页卡片的顺序 / 显隐。
     *
     * 不给每个 VIN 各存一份：卡片顺序是「个人习惯」，换车不该重置排版；
     * 而账单、打卡这些是车辆自己的账，必须按 VIN 隔离。所以它放在 prefs 而不是 JSON 里。
     */
    private val _cardOrder = mutableStateOf(JournalCardLayout.parse(prefs.getString(KEY_CARD_ORDER, null)))
    val cardOrder: State<List<JournalCardId>> get() = _cardOrder

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
     * 保存概览页的卡片顺序 / 显隐。
     *
     * 空列表是合法值（用户把卡片全删了），所以这里不能做「空的就回退默认」。
     */
    fun setCardOrder(cards: List<JournalCardId>) {
        val safe = cards.distinct()
        if (safe == _cardOrder.value) return
        _cardOrder.value = safe
        prefs.edit().putString(KEY_CARD_ORDER, JournalCardLayout.serialize(safe)).apply()
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
                // 打卡即达成心愿：心愿单里同一个城市顺手划掉，不留一条「已去过的愿望」
                wishes = data.wishes.filterNot { it.id == city.id },
            )
        }
        return true
    }

    fun removeCheckin(id: String) = mutate { data ->
        data.copy(checkins = data.checkins.filterNot { it.id == id })
    }

    // ---------------- 城市心愿单 ----------------

    /**
     * 加入心愿单。
     *
     * 已经去过（或已经在单子里）就不加，返回 false 让 UI 提示一句 ——
     * 心愿单里出现一个已经插了旗的城市，只会让人以为自己记重了。
     */
    fun addWish(city: City): Boolean {
        val data = ensureLoaded()
        if (data.checkins.any { it.id == city.id }) return false
        if (data.wishes.any { it.id == city.id }) return false
        mutate { current ->
            current.copy(
                wishes = current.wishes + CityWish(
                    id = city.id,
                    city = city.name,
                    province = city.province,
                    added = System.currentTimeMillis(),
                ),
            )
        }
        return true
    }

    fun removeWish(id: String) = mutate { data ->
        data.copy(wishes = data.wishes.filterNot { it.id == id })
    }

    /** 心愿兑现：把心愿单里的一条直接转成打卡（打卡后自动出单）。 */
    fun fulfillWish(wishId: String, date: Long = JournalDates.todayStart()): Boolean {
        val city = CityCatalog.byId(wishId) ?: return false
        return checkin(city, date)
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
     *
     * 跨天换基线时会把「上次记录 → 今天」这段的增量补进中间那几天（见 [fillGap]）：
     * 否则中间几天没打开 App 的话，这几天的里程会凭空消失 —— 用户要的是
     * 「所有数据都留着」，而不是只有打开 App 的那几天。
     */
    fun recordOdometer(totalKm: Double?) {
        val total = totalKm?.takeIf { it.isFinite() && it >= 0.0 && it < 2_000_000.0 } ?: return
        val today = JournalDates.dayKey(JournalDates.todayStart())
        mutate { data ->
            val baseDay = data.odometerDay
            val baseKm = data.odometerKm
            if (baseDay != today || baseKm == null) {
                return@mutate data.copy(
                    dailyKm = fillGap(data.dailyKm, baseDay, baseKm, total, today),
                    odometerDay = today,
                    odometerKm = total,
                )
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

    /**
     * 把 `(baseDay, today]` 这段的总里程增量按天平摊进 dailyKm。
     *
     * 只在间隔 1~[MAX_GAP_DAYS] 天时补：这段时间车通常每天都在跑，平摊比
     * 「整段都记到打开 App 那一天」更接近真实。间隔再长（比如一个月没开 App）
     * 就没有信息量了，画在图上只是一段凭空的斜坡，只换基线、不补数。
     *
     * 已存在的更大值不覆盖 —— 服务端近 7 日明细比这里的估算准，取大才是对的。
     */
    private fun fillGap(
        dailyKm: Map<String, Double>,
        baseDay: String?,
        baseKm: Double?,
        total: Double,
        today: String,
    ): Map<String, Double> {
        if (baseDay == null || baseKm == null || baseDay == today) return dailyKm
        val from = JournalDates.parseDayKey(baseDay) ?: return dailyKm
        val to = JournalDates.parseDayKey(today) ?: return dailyKm
        val gap = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()
        if (gap <= 0 || gap > MAX_GAP_DAYS) return dailyKm
        val delta = total - baseKm
        if (delta <= 0.0) return dailyKm
        val perDay = delta / gap
        // 日均 800km 已经超出家用车的可信范围，宁可不补也不要把脏数据铺到图上
        if (perDay <= 0.0 || perDay > MAX_DAILY_KM) return dailyKm
        val merged = dailyKm.toMutableMap()
        for (i in 1..gap) {
            val key = JournalDates.dayKey(from.plusDays(i.toLong()))
            val old = merged[key] ?: 0.0
            if (perDay > old) merged[key] = perDay
        }
        return merged
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

    /**
     * 里程趋势：把**本地全量**每日里程按 [range] 聚合成固定格数。
     *
     * 「全量」指零记 JSON 里的 `dailyKm`，不是官方接口那 7 天 —— 官方近 7 日明细
     * 只用来校准最近 7 天，更早的数据靠这里一天天累积，永远不会被裁掉。
     */
    fun kmSeries(
        range: KmRange,
        today: java.time.LocalDate = JournalDates.today(),
    ): List<KmPoint> = MileageSeries.of(ensureLoaded().dailyKm, range, today)

    // ---------------- 保养 / 保险提醒 ----------------

    /** 设置或清除保养计划（传 null 即关闭提醒）。 */
    fun setMaintenance(plan: MaintenancePlan?) = mutate { it.copy(maintenance = plan) }

    /** 设置或清除保险计划（传 null 即关闭提醒）。 */
    fun setInsurance(plan: InsurancePlan?) = mutate { it.copy(insurance = plan) }

    // ---------------- 备份 / 迁移 ----------------

    /**
     * 把本机**所有车辆**的零记数据打成一段 JSON 文本。
     *
     * 当前车直接用内存里的那份覆盖磁盘上的：落盘是异步协程，刚记完一笔就点导出时，
     * 磁盘上可能还是上一版，导出会少最后一笔。
     */
    fun exportBackup(appVersion: String, now: Long = System.currentTimeMillis()): String {
        val current = ensureLoaded()
        val currentVin = vinProvider()
        val all = store.loadAll().toMutableMap()
        all[store.keyOf(currentVin)] = current
        val vehicles = all.toSortedMap().map { (key, data) -> JournalBackup.Vehicle(key, data) }
        return JournalBackup.encode(
            JournalBackup.Payload(
                exportedAt = now,
                appVersion = appVersion,
                vehicles = vehicles,
                cardOrder = prefs.getString(KEY_CARD_ORDER, null),
            ),
        )
    }

    /**
     * 导入一份备份，与**每辆车**本机已有的数据合并（规则见 [JournalBackup.merge]）。
     *
     * 直接写盘、不走 [mutate]：导入可能是用户换机后唯一一次操作，
     * 不该等着某个协程排队；而且这里要一次处理多辆车，`mutate` 只认当前车。
     */
    fun importBackup(payload: JournalBackup.Payload): JournalBackup.Summary {
        val currentVin = vinProvider()
        val currentKey = store.keyOf(currentVin)
        var newCosts = 0
        var newCheckins = 0
        var newWishes = 0
        var newKmDays = 0
        payload.vehicles.forEach { vehicle ->
            val isCurrent = vehicle.vin == currentKey
            val local = if (isCurrent) ensureLoaded() else store.load(vehicle.vin)
            val merged = JournalBackup.merge(local, vehicle.data)
            if (merged == local) return@forEach
            newCosts += merged.costs.size - local.costs.size
            newCheckins += merged.checkins.size - local.checkins.size
            newWishes += merged.wishes.size - local.wishes.size
            newKmDays += merged.dailyKm.size - local.dailyKm.size
            if (isCurrent) {
                loadedVin = currentVin
                _data.value = merged
            }
            store.save(if (isCurrent) currentVin else vehicle.vin, merged)
        }
        // 排版偏好：本机没动过才用备份里的，避免把当前手机的排版冲掉
        var cardOrderRestored = false
        val backupOrder = payload.cardOrder
        if (!backupOrder.isNullOrBlank() && prefs.getString(KEY_CARD_ORDER, null).isNullOrBlank()) {
            val cards = JournalCardLayout.parse(backupOrder)
            if (cards.isNotEmpty()) {
                _cardOrder.value = cards
                prefs.edit().putString(KEY_CARD_ORDER, JournalCardLayout.serialize(cards)).apply()
                cardOrderRestored = true
            }
        }
        return JournalBackup.Summary(
            vehicles = payload.vehicles.size,
            newCosts = newCosts,
            newCheckins = newCheckins,
            newWishes = newWishes,
            newKmDays = newKmDays,
            cardOrderRestored = cardOrderRestored,
        )
    }

    private fun newId(): String = UUID.randomUUID().toString().take(8)
}

/** 打卡页柱状图用的一天。 */
data class DailyKm(
    val key: String,
    val date: java.time.LocalDate,
    val km: Double,
)

/** 概览页卡片顺序的存储键（与账单数据分开：这是排版偏好，不按 VIN 隔离）。 */
private const val KEY_CARD_ORDER = "card_order"

/** 每辆车的文件名：`journal_<vin>.json`（导出的 `vehicles[].vin` 就是中间那一段）。 */
private const val FILE_PREFIX = "journal_"

private const val FILE_SUFFIX = ".json"

/** 跨天补里程的最大间隔（天）：超过这个跨度就不做平摊，只换基线。 */
private const val MAX_GAP_DAYS = 7

/** 单日里程的可信上限：超过它认为是仪表 / 数据异常，宁可漏记也不写脏值。 */
private const val MAX_DAILY_KM = 800.0
