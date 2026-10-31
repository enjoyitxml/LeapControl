package com.leapcontrol.app.journal

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.DailyMileage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 零记的本地仓库：**一个文件**装下所有车辆（`filesDir/journal/journal.json`）。
 *
 * 全部本地、不联网、不参与任何统计上报 —— 花费和行程是最私人不过的数据。
 *
 * 数据安全上是分层的，任何一层单独失效都不该让账本消失：
 *  1. **原子写**：先写 `journal.json.new`（含 fsync），再 `rename` 覆盖主文件。
 *     旧实现是「先 delete 再 rename」，进程在两步之间被杀就两个文件都没有。
 *  2. **上一版留档**：每次覆盖前把旧主文件写成 `journal.json.bak`。
 *  3. **日快照**：每天首次写入前存一份 `snapshots/journal-YYYYMMDD.json`，滚动保留 [SNAPSHOT_KEEP] 份。
 *  4. **读失败禁写**：主文件、备份、快照都解析不出来时置 [Source.FAILED]，
 *     [save] 直接拒绝 —— 「读成空账本 → 把空账本写回去」是最彻底的一种丢数据方式。
 *  5. **旧文件迁移留档**：老版本 `journal_<vin>.json` 合并后改名 `*.migrated`，不删除。
 *
 * 文件内容格式见 [JournalVault]。
 */
class JournalStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 最近一次读盘用的是哪个来源。 */
    enum class Source {
        /** 主文件。 */
        MAIN,

        /** 主文件坏了，用的是上一版备份。 */
        BACKUP,

        /** 主文件与备份都坏了，用的是日快照。 */
        SNAPSHOT,

        /** 从老版本「一车一文件」迁移而来。 */
        LEGACY,

        /** 全新安装，没有任何数据。 */
        NONE,

        /** 磁盘上有文件但全部解析失败 —— **禁止写入**，等用户处理。 */
        FAILED,
    }

    private val dir = File(context.filesDir, "journal")
    private val snapDir = File(dir, "snapshots")
    private val mainFile = File(dir, MAIN_NAME)
    private val bakFile = File(dir, "$MAIN_NAME.bak")

    @Volatile
    var readSource: Source = Source.NONE
        private set

    @Volatile
    private var cache: Vault? = null
    private var legacyChecked = false

    /** 最近一次写盘失败的原因（UI 用来提示「数据没存上」）。 */
    @Volatile
    var lastWriteError: String? = null
        private set

    /** VIN → 文件内的 key。备份/迁移按同一套规则认车，所以单独暴露。 */
    fun keyOf(vin: String): String =
        vin.ifBlank { "default" }.replace(Regex("""[^A-Za-z0-9_-]"""), "_")

    // ---------------- 读 ----------------

    /** 所有车辆的数据（key 与 [keyOf] 一致）。 */
    fun loadAll(): Map<String, JournalData> = vault().cars.mapValues { it.value.data }

    fun load(vin: String): JournalData = loadAll()[keyOf(vin)] ?: JournalData.EMPTY

    /** 最后一次成功写盘的时间。 */
    fun lastSavedAt(): Long = vault().savedAt

    private fun vault(): Vault {
        cache?.let { return it }
        val (loaded, source) = readFromDisk()
        readSource = source
        val merged = if (legacyChecked) loaded else mergeLegacy(loaded)
        legacyChecked = true
        cache = merged
        // 迁移产生了新内容：立刻落盘一次，之后就走正常流程
        if (merged !== loaded) write(merged, merged.lastKey)
        return merged
    }

    private fun readFromDisk(): Pair<Vault, Source> {
        mainFile.takeIf { it.isFile }?.let { file ->
            JournalVault.decode(runCatching { file.readText() }.getOrNull())
                ?.let { return it to Source.MAIN }
        }
        bakFile.takeIf { it.isFile }?.let { file ->
            JournalVault.decode(runCatching { file.readText() }.getOrNull())
                ?.let { return it to Source.BACKUP }
        }
        latestSnapshot()?.let { file ->
            JournalVault.decode(runCatching { file.readText() }.getOrNull())
                ?.let { return it to Source.SNAPSHOT }
        }
        // 磁盘上有文件却一份都解不开：真的坏了，标记 FAILED 让上层禁止写入
        val broken = mainFile.exists() || bakFile.exists()
        return Vault(emptyMap(), null, 0L) to if (broken) Source.FAILED else Source.NONE
    }

    private fun latestSnapshot(): File? =
        snapDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SNAPSHOT_PREFIX) && it.name.endsWith(".json") }
            ?.maxByOrNull { it.name }

    // ---------------- 写 ----------------

    fun save(vin: String, data: JournalData): Boolean {
        val key = keyOf(vin)
        return saveMany(mapOf(key to data), key)
    }

    /** 一次写入多辆车（导入用）。 */
    fun saveMany(updates: Map<String, JournalData>, lastKey: String? = null): Boolean {
        if (readSource == Source.FAILED) return false
        val current = vault()
        val cars = current.cars.toMutableMap()
        val now = clock()
        updates.forEach { (key, data) ->
            cars[key] = VaultCar(key, data, now)
        }
        return write(current.copy(cars = cars), lastKey ?: current.lastKey)
    }

    private fun write(v: Vault, lastKey: String?): Boolean {
        if (readSource == Source.FAILED) return false
        val now = clock()
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            // 只有「主文件本身是好的」才把它转成上一版备份。
            // 如果这次是从 .bak / 快照恢复出来的（主文件已经坏了），
            // 把坏内容写回 .bak 等于把唯一的良好副本也弄脏。
            if (readSource == Source.MAIN) {
                val old = mainFile.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
                if (!old.isNullOrBlank()) {
                    writeAtomic(bakFile, old)
                    snapshotDaily(old, now)
                }
            }
            writeAtomic(mainFile, JournalVault.encode(v.cars, lastKey, now))
            cache = v.copy(lastKey = lastKey, savedAt = now)
            readSource = Source.MAIN
            lastWriteError = null
            true
        }.getOrElse { error ->
            lastWriteError = error.message ?: error.javaClass.simpleName
            false
        }
    }

    /**
     * 原子写：临时文件 → fsync → rename。
     *
     * 同目录 rename 是原子的，所以任何时刻读到的要么是旧的完整文件、要么是新的完整文件，
     * 不会出现半个 JSON。旧实现先 delete 再 rename，那个空档就是「数据凭空消失」的来源。
     */
    private fun writeAtomic(file: File, text: String) {
        val parent = file.parentFile ?: throw IOException("没有父目录")
        if (!parent.exists()) parent.mkdirs()
        val tmp = File(parent, file.name + ".new")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (tmp.renameTo(file)) return
        // 少数文件系统不允许覆盖式 rename：先把旧文件挪开，失败就把旧文件放回去
        val parked = File(parent, file.name + ".park")
        if (file.exists() && file.renameTo(parked)) {
            if (!tmp.renameTo(file)) {
                parked.renameTo(file)
                throw IOException("无法写入 ${file.name}")
            }
            parked.delete()
        } else {
            throw IOException("无法写入 ${file.name}")
        }
    }

    private fun snapshotDaily(text: String, now: Long) {
        val target = File(snapDir, "$SNAPSHOT_PREFIX${dayStamp(now)}.json")
        if (target.exists()) return
        runCatching {
            if (!snapDir.exists()) snapDir.mkdirs()
            writeAtomic(target, text)
            trimSnapshots()
        }
    }

    private fun trimSnapshots() {
        val all = snapDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SNAPSHOT_PREFIX) && it.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?: return
        all.drop(SNAPSHOT_KEEP).forEach { runCatching { it.delete() } }
    }

    /**
     * 迁移老版本的「一车一文件」。
     *
     * 已经在新文件里的那辆车做**并集**（不丢任何一侧），旧文件改名 `*.migrated` 留档。
     * 这条路径要能反复执行而不产生副作用：改名之后就再也匹配不上，所以只会跑一次。
     */
    private fun mergeLegacy(v: Vault): Vault {
        if (!dir.exists()) return v
        val legacy = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(LEGACY_PREFIX) && it.name.endsWith(LEGACY_SUFFIX) }
            ?.sortedBy { it.lastModified() }
            ?: return v
        if (legacy.isEmpty()) return v
        val cars = v.cars.toMutableMap()
        var changed = false
        legacy.forEach { file ->
            val key = file.name.removePrefix(LEGACY_PREFIX).removeSuffix(LEGACY_SUFFIX)
                .ifBlank { "default" }
            val data = runCatching {
                JournalData.fromJson(JSONObject(file.readText()))
            }.getOrNull() ?: return@forEach
            if (data.isEmpty) return@forEach
            val existing = cars[key]?.data
            cars[key] = VaultCar(
                key = key,
                data = if (existing == null || existing.isEmpty) data
                else JournalBackup.merge(existing, data),
                updatedAt = file.lastModified(),
            )
            changed = true
            runCatching { file.renameTo(File(dir, file.name + ".migrated")) }
        }
        return if (changed) v.copy(cars = cars) else v
    }

    // ---------------- 找回：扫描本机所有可恢复来源 ----------------

    /**
     * 列出磁盘上所有还能读出内容的零记数据。
     *
     * 用途是「更新后数据不见了」的救援：数据很可能还在，只是 key 对不上或者主文件坏了。
     * 每一份都带上来源标签与统计，用户在设置里能看到、能选择合并回当前车辆。
     */
    fun sources(): List<JournalSource> {
        val list = ArrayList<JournalSource>()
        val seen = HashSet<String>()

        fun scan(file: File, label: String, kind: JournalSource.Kind) {
            if (!file.isFile) return
            if (!seen.add(file.absolutePath)) return
            val text = runCatching { file.readText() }.getOrNull() ?: return
            val fallbackKey = file.name
                .removePrefix(LEGACY_PREFIX)
                .substringBefore('.')
                .ifBlank { "default" }
            val cars = JournalVault.decodeLoose(text, fallbackKey) ?: return
            cars.forEach { (carKey, data) ->
                if (data.isEmpty) return@forEach
                list += JournalSource(
                    id = file.absolutePath + "#" + carKey,
                    label = label,
                    carKey = carKey,
                    updatedAt = file.lastModified(),
                    size = file.length(),
                    data = data,
                    kind = kind,
                )
            }
        }

        scan(mainFile, "当前数据文件", JournalSource.Kind.MAIN)
        scan(bakFile, "上一版备份", JournalSource.Kind.BACKUP)
        snapDir.listFiles()
            ?.sortedByDescending { it.name }
            ?.forEach { scan(it, "日快照 ${snapshotLabel(it.name)}", JournalSource.Kind.SNAPSHOT) }
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { file ->
                when {
                    file.name.startsWith(LEGACY_PREFIX) && file.name.endsWith(LEGACY_SUFFIX) ->
                        scan(file, "旧版文件", JournalSource.Kind.LEGACY)

                    file.name.endsWith(".migrated") ->
                        scan(file, "迁移留档", JournalSource.Kind.LEGACY)

                    file.name.endsWith(".new") || file.name.endsWith(".tmp") ->
                        scan(file, "写入中断的临时文件", JournalSource.Kind.TEMP)

                    else -> Unit
                }
            }
        return list.sortedByDescending { it.updatedAt }
    }

    /** 设置页的「数据安全」摘要。 */
    fun status(): JournalStatus = JournalStatus(
        source = readSource,
        savedAt = runCatching { lastSavedAt() }.getOrDefault(0L),
        carCount = runCatching { loadAll().count { !it.value.isEmpty } }.getOrDefault(0),
        snapshotCount = snapDir.listFiles()?.count {
            it.isFile && it.name.startsWith(SNAPSHOT_PREFIX) && it.name.endsWith(".json")
        } ?: 0,
        lastWriteError = lastWriteError,
    )
}

/** 一份可恢复的本地数据（[id] 唯一，[label] 是来源说明）。 */
data class JournalSource(
    val id: String,
    val label: String,
    val carKey: String,
    val updatedAt: Long,
    val size: Long,
    val data: JournalData,
    val kind: Kind,
) {
    enum class Kind { MAIN, BACKUP, SNAPSHOT, LEGACY, TEMP }
}

/** 存储层当前状态（给设置页显示）。 */
data class JournalStatus(
    val source: JournalStore.Source,
    val savedAt: Long,
    val carCount: Int,
    val snapshotCount: Int,
    val lastWriteError: String? = null,
)

/** 「已自动接续历史数据」的提示内容。 */
data class JournalRecoveryNote(
    val fromKey: String,
    val costs: Int,
    val checkins: Int,
    val kmDays: Int,
    /** true = 自动接续成功；false = 本机存在多份历史，需要用户手动认领。 */
    val auto: Boolean = true,
)

/**
 * 零记的运行时状态。
 *
 * UI 直接读 [data]（Compose State）：改完立刻生效，不等落盘；
 * 落盘放到 IO 协程里做，避免「点保存 → 主线程写文件 → 掉帧」。
 */
class JournalRuntime(
    context: Context,
    private val vinProvider: () -> String,
) {
    private val appContext = context.applicationContext
    private val store = JournalStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("leapcontrol_journal", Context.MODE_PRIVATE)

    private val _data = mutableStateOf(JournalData.EMPTY)
    val data: State<JournalData> get() = _data

    /** 自动接续 / 需要认领历史数据时给 UI 的提示，null 表示一切正常。 */
    private val _recovery = mutableStateOf<JournalRecoveryNote?>(null)
    val recovery: State<JournalRecoveryNote?> get() = _recovery

    /** 存储层状态（读来源、快照数、写失败原因）。 */
    private val _status = mutableStateOf<JournalStatus?>(null)
    val status: State<JournalStatus?> get() = _status

    /**
     * 概览页卡片的顺序 / 显隐。
     *
     * 不给每个 VIN 各存一份：卡片顺序是「个人习惯」，换车不该重置排版；
     * 而账单、打卡这些是车辆自己的账，必须按 VIN 隔离。
     */
    private val _cardOrder = mutableStateOf(JournalCardLayout.parse(prefs.getString(KEY_CARD_ORDER, null)))
    val cardOrder: State<List<JournalCardId>> get() = _cardOrder

    private var loadedVin: String? = null
    private var bootChecked = false
    private var lastAutoBackupCheck = 0L

    /** 当前数据属于哪个 VIN（UI 用它判断要不要重读）。 */
    val vin: String get() = loadedVin ?: vinProvider()

    /** 确保内存里是「当前这辆车」的数据；VIN 变了就重新读盘。 */
    fun ensureLoaded(): JournalData {
        val vin = vinProvider()
        if (vin != loadedVin) {
            loadedVin = vin
            _data.value = loadFor(vin)
        }
        if (!bootChecked) {
            bootChecked = true
            // 启动后兜一次自动备份：用户当天只看看不记账时，那天也该有一份外部副本
            scope.launch { autoBackupIfDue() }
        }
        return _data.value
    }

    /**
     * 读当前车的数据，并在「读不到」时尝试接续本机历史。
     *
     * 更新后 VIN 变了、首次启动 VIN 还没就绪、重新登录换了账号，都会让当前 key 查不到数据。
     * 这时候如果本机**恰好只有一份**非空历史，直接接续过来 —— 磁盘上的账本来是好的，
     * 让它在界面上消失才是最不应该发生的事。
     */
    private fun loadFor(vin: String): JournalData {
        val key = store.keyOf(vin)
        val all = store.loadAll()
        val mine = all[key] ?: JournalData.EMPTY
        if (!mine.isEmpty) return mine
        val others = all.filterKeys { it != key }.filterValues { !it.isEmpty }
        if (others.isEmpty()) return mine
        val sole = others.entries.singleOrNull()
        if (sole == null) {
            _recovery.value = JournalRecoveryNote(
                fromKey = others.keys.joinToString("、"),
                costs = others.values.sumOf { it.costs.size },
                checkins = others.values.sumOf { it.checkins.size },
                kmDays = others.values.sumOf { it.dailyKm.size },
                auto = false,
            )
            return mine
        }
        // 接续：把那份数据挂到当前 key 上（原 key 原样保留，不删）
        store.saveMany(mapOf(key to sole.value), lastKey = key)
        _recovery.value = JournalRecoveryNote(
            fromKey = sole.key,
            costs = sole.value.costs.size,
            checkins = sole.value.checkins.size,
            kmDays = sole.value.dailyKm.size,
            auto = true,
        )
        return sole.value
    }

    /** 用户看过了接续提示。 */
    fun dismissRecovery() {
        _recovery.value = null
    }

    /** 重新读盘（找回数据后用）。 */
    fun reload() {
        loadedVin = vinProvider()
        _data.value = loadFor(loadedVin.orEmpty())
        _status.value = store.status()
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

    private fun mutate(block: (JournalData) -> JournalData) {
        val vin = vinProvider()
        if (vin != loadedVin) {
            loadedVin = vin
            _data.value = loadFor(vin)
        }
        val next = block(_data.value)
        if (next == _data.value) return
        _data.value = next
        val snapshot = next
        scope.launch {
            store.save(vin, snapshot)
            autoBackupIfDue()
        }
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
        val vehicles = all.toSortedMap()
            .filterValues { !it.isEmpty }
            .map { (key, data) -> JournalBackup.Vehicle(key, data) }
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
        val writes = LinkedHashMap<String, JournalData>()
        payload.vehicles.forEach { vehicle ->
            val isCurrent = vehicle.vin == currentKey
            val local = if (isCurrent) ensureLoaded() else store.load(vehicle.vin)
            val merged = JournalBackup.merge(local, vehicle.data)
            if (merged == local) return@forEach
            newCosts += merged.costs.size - local.costs.size
            newCheckins += merged.checkins.size - local.checkins.size
            newWishes += merged.wishes.size - local.wishes.size
            newKmDays += merged.dailyKm.size - local.dailyKm.size
            writes[store.keyOf(vehicle.vin)] = merged
            if (isCurrent) {
                loadedVin = currentVin
                _data.value = merged
            }
        }
        if (writes.isNotEmpty()) store.saveMany(writes, lastKey = currentKey)
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
        _status.value = store.status()
        return JournalBackup.Summary(
            vehicles = payload.vehicles.size,
            newCosts = newCosts,
            newCheckins = newCheckins,
            newWishes = newWishes,
            newKmDays = newKmDays,
            cardOrderRestored = cardOrderRestored,
        )
    }

    // ---------------- 找回历史数据 ----------------

    /** 本机所有可恢复的数据来源（含上一版备份、日快照、旧版文件、写入中断的临时文件）。 */
    fun sources(): List<JournalSource> = store.sources()

    /** 存储层状态。 */
    fun refreshStatus(): JournalStatus = store.status().also { _status.value = it }

    /** 把某一份历史数据合并进当前车辆（只增不减，不会删掉现有记录）。 */
    fun adoptSource(source: JournalSource): Boolean {
        val current = ensureLoaded()
        val merged = JournalBackup.merge(current, source.data)
        if (merged == current) return false
        val vin = vinProvider()
        loadedVin = vin
        _data.value = merged
        store.save(vin, merged)
        _status.value = store.status()
        _recovery.value = null
        return true
    }

    // ---------------- 自动备份到公共下载目录 ----------------

    private fun autoBackupIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastAutoBackupCheck < AUTO_BACKUP_CHECK_MS) return
        lastAutoBackupCheck = now
        runCatching {
            if (!JournalAutoBackup.isEnabled(appContext)) return
            if (!JournalAutoBackup.isDue(appContext, now)) return
            val text = exportBackup(BuildConfig.VERSION_NAME, now)
            JournalAutoBackup.write(appContext, text, now)
        }
    }

    /** 手动触发一次自动备份（设置页的「立即备份到下载目录」）。 */
    fun backupToDownloadsNow(): Result<Unit> = runCatching {
        val text = exportBackup(BuildConfig.VERSION_NAME)
        JournalAutoBackup.write(appContext, text, System.currentTimeMillis())
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

/** 主数据文件名：固定名，与 VIN 无关。 */
private const val MAIN_NAME = "journal.json"

/** 老版本「一车一文件」的命名：合并后改名 `*.migrated` 留档。 */
private const val LEGACY_PREFIX = "journal_"

private const val LEGACY_SUFFIX = ".json"

/** 日快照前缀：`snapshots/journal-YYYYMMDD.json`。 */
private const val SNAPSHOT_PREFIX = "journal-"

/** 快照保留份数（一天一份，约两周）。 */
private const val SNAPSHOT_KEEP = 14

/** 跨天补里程的最大间隔（天）：超过这个跨度就不做平摊，只换基线。 */
private const val MAX_GAP_DAYS = 7

/** 单日里程的可信上限：超过它认为是仪表 / 数据异常，宁可漏记也不写脏值。 */
private const val MAX_DAILY_KM = 800.0

/** 自动备份的内存节流：10 分钟内最多检查一次（真正是否要写由 prefs 里的日期决定）。 */
private const val AUTO_BACKUP_CHECK_MS = 10 * 60_000L

private fun dayStamp(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.US))

/** `journal-20261031.json` → `2026-10-31`。 */
private fun snapshotLabel(name: String): String {
    val digits = name.removePrefix(SNAPSHOT_PREFIX).substringBefore('.').filter { it.isDigit() }
    if (digits.length != 8) return digits
    return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
}
