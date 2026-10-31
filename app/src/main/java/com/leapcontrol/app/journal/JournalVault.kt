package com.leapcontrol.app.journal

import org.json.JSONObject

/**
 * 零记在本机的存储格式（schema 2）：**一个文件装下所有车辆**。
 *
 * 为什么从「一辆车一个文件」改成「一个文件装全部」：
 * 文件名里带 VIN 意味着「数据能不能看见」取决于当前解析出来的 VIN。
 * 更新后首次启动时 VIN 还没就绪、换账号、换车，都会让 App 去读一个不存在的
 * `journal_default.json`，界面上就是「数据全丢了」—— 而磁盘上那份其实好好的。
 * 现在文件固定叫 `journal/journal.json`，VIN 只是文件内的一个 key，
 * 所以「读到哪个文件」不再依赖任何运行时状态。
 *
 * 文件长这样：
 * ```
 * { "schema": 2, "savedAt": 1761..., "lastKey": "L...",
 *   "cars": { "L...": { "updatedAt": 1761..., "data": { ...JournalData... } } } }
 * ```
 * [decode] 解析不出来一律返回 null —— 调用方必须把它当成「这份不能用」，
 * 而不是当成空账本继续往下写（那才是真正把数据写没的一步）。
 */
data class VaultCar(
    val key: String,
    val data: JournalData,
    val updatedAt: Long,
)

data class Vault(
    val cars: Map<String, VaultCar>,
    val lastKey: String?,
    val savedAt: Long,
) {
    val isEmpty: Boolean get() = cars.values.all { it.data.isEmpty }

    fun find(key: String): JournalData? = cars[key]?.data

    /** 唯一一份非空数据（用于「当前车没有数据、但本机只有一份历史」的自动接续）。 */
    fun soleNonEmpty(excludeKey: String? = null): VaultCar? {
        val candidates = cars.values.filter { !it.data.isEmpty && it.key != excludeKey }
        return candidates.singleOrNull()
    }
}

object JournalVault {

    /** 当前格式版本。读到更高版本的字段不会报错，只是忽略（[JournalData.fromJson] 本来就宽松）。 */
    const val SCHEMA = 2

    fun encode(cars: Map<String, VaultCar>, lastKey: String?, savedAt: Long): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("savedAt", savedAt)
        lastKey?.takeIf { it.isNotBlank() }?.let { root.put("lastKey", it) }
        root.put(
            "cars",
            JSONObject().apply {
                cars.toSortedMap().forEach { (key, car) ->
                    put(
                        key,
                        JSONObject().apply {
                            put("updatedAt", car.updatedAt)
                            put("data", car.data.toJson())
                        },
                    )
                }
            },
        )
        // 缩进两格：出问题时用户拿文本编辑器就能看懂，也方便直接复制给我们排查
        return root.toString(2)
    }

    /** 解析整份数据文件；结构不对返回 null（**不是**空账本）。 */
    fun decode(text: String?): Vault? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val carsObj = root.optJSONObject("cars") ?: return null
        val cars = LinkedHashMap<String, VaultCar>()
        val keys = carsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.isBlank()) continue
            val item = carsObj.optJSONObject(key) ?: continue
            cars[key] = VaultCar(
                key = key,
                data = JournalData.fromJson(item.optJSONObject("data")),
                updatedAt = item.optLong("updatedAt", 0L),
            )
        }
        return Vault(
            cars = cars,
            lastKey = root.optString("lastKey").takeIf { it.isNotBlank() },
            savedAt = root.optLong("savedAt", 0L),
        )
    }

    /**
     * 宽松解析任意一份磁盘内容：新格式（vault）或旧格式（裸 JournalData）。
     *
     * 「找回历史数据」要读的是各种残骸 —— 老版本的 `journal_<vin>.json` 是裸
     * JournalData，写了一半的 `.tmp` 也可能是。两种都认，才不会漏掉可恢复的那份。
     */
    fun decodeLoose(text: String?, fallbackKey: String): Map<String, JournalData>? {
        decode(text)?.let { vault ->
            return vault.cars.mapValues { it.value.data }
        }
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val raw = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val data = JournalData.fromJson(raw)
        // 完全没有零记字段的 JSON（比如备份文件、随便一个 json）不要当成数据来源
        if (data.isEmpty && !raw.has("v")) return null
        return mapOf(fallbackKey to data)
    }
}
