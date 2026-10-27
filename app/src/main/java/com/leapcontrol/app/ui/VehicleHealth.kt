package com.leapcontrol.app.ui

import com.leapcontrol.app.VehicleCapabilities

/**
 * 车辆体检：基于当前车况快照 [CarStatus] 做规则判断，产出可读的体检报告。
 *
 * 设计原则：**只看真实信号**。某一路信号缺失就给中性结论并说明「无数据」，
 * 绝不能把「没上报」当成「正常」，否则用户会被虚假的「全部正常」误导。
 * 每一项结论都尽量带上可一键执行的修复指令（下发到已有控车通道）。
 */

enum class HealthLevel {
    GOOD,
    WARNING,
    CRITICAL;

    companion object {
        fun worst(levels: List<HealthLevel>): HealthLevel =
            when {
                levels.contains(CRITICAL) -> CRITICAL
                levels.contains(WARNING) -> WARNING
                else -> GOOD
            }
    }
}

data class HealthCheckItem(
    val title: String,
    val detail: String,
    val level: HealthLevel,
    /** 可一键修复时下发的指令名（Commands.build 或 ControlActions.resolveCommand 的输入）。 */
    val fixCommand: String? = null,
    val fixLabel: String? = null,
)

data class HealthSystemReport(
    val name: String,
    val statusText: String,
    val level: HealthLevel,
    val items: List<HealthCheckItem>,
) {
    /** 有异常的系统默认展开，正常的默认折叠。 */
    val defaultExpanded: Boolean get() = level != HealthLevel.GOOD
}

data class VehicleHealthReport(
    val score: Int,
    val summary: String,
    val level: HealthLevel,
    val systems: List<HealthSystemReport>,
    val issues: List<HealthCheckItem>,
)

object VehicleHealthDiagnostics {

    /** 低电量提醒阈值：低于此值建议尽快补电。 */
    private const val LOW_SOC = 20
    /** 严重低电量：继续停放可能损伤动力电池。 */
    private const val CRITICAL_SOC = 10
    /** 电池温度异常区间（摄氏度）。 */
    private const val BATTERY_TEMP_COLD = -10.0
    private const val BATTERY_TEMP_HOT = 55.0
    /** 胎压异常区间（kPa），乘用车常规冷胎胎压约 230-280。 */
    private const val TIRE_LOW = 200.0
    private const val TIRE_HIGH = 330.0
    /** 车内温度偏高提醒阈值（夏季暴晒后常见）。 */
    private const val INTERIOR_TEMP_HOT = 40.0

    fun evaluate(status: CarStatus?, capabilities: VehicleCapabilities = VehicleCapabilities.EMPTY): VehicleHealthReport {
        if (status == null) {
            return VehicleHealthReport(
                score = 0,
                summary = "车况待同步",
                level = HealthLevel.WARNING,
                systems = emptyList(),
                issues = listOf(
                    HealthCheckItem(
                        title = "数据未同步",
                        detail = "请先下拉刷新车况，获取最新信号后再体检",
                        level = HealthLevel.WARNING,
                    )
                )
            )
        }

        val powertrain = evaluatePowertrain(status)
        val chassis = evaluateChassis(status)
        val body = evaluateBody(status, capabilities)
        val climate = evaluateClimate(status)
        val systems = listOf(powertrain, chassis, body, climate)

        val issues = systems.flatMap { it.items }.filter { it.level != HealthLevel.GOOD }
        val level = HealthLevel.worst(systems.map { it.level })
        // 扣分：严重项 -15，警告项 -6，保底 0 分。
        // 用 fold(0) 而非 sumOf：字面量 15/6 既能推断成 Int 也能推断成 Long，
        // 会让 sumOf 的 Int/Long 两个重载产生歧义。
        val deduction = issues.fold(0) { acc, item ->
            acc + if (item.level == HealthLevel.CRITICAL) 15 else 6
        }
        val score = maxOf(0, minOf(100, 100 - deduction))
        return VehicleHealthReport(
            score = score,
            summary = buildSummary(level, issues.size),
            level = level,
            systems = systems,
            issues = issues,
        )
    }

    private fun buildSummary(level: HealthLevel, issueCount: Int): String = when {
        issueCount == 0 -> "各系统状态良好"
        level == HealthLevel.CRITICAL -> "发现 $issueCount 项需要处理，其中含严重项"
        else -> "发现 $issueCount 项建议处理"
    }

    // ---------------- 1. 动力与三电 ----------------

    private fun evaluatePowertrain(status: CarStatus): HealthSystemReport {
        val items = mutableListOf<HealthCheckItem>()

        // 电量
        val soc = status.soc
        items += when {
            soc <= CRITICAL_SOC -> HealthCheckItem(
                title = "电量严重偏低",
                detail = "当前电量 $soc%，建议立即充电，长期亏电停放会损伤动力电池",
                level = HealthLevel.CRITICAL,
            )
            soc <= LOW_SOC -> HealthCheckItem(
                title = "电量偏低",
                detail = "当前电量 $soc%，建议尽快补电",
                level = HealthLevel.WARNING,
            )
            else -> HealthCheckItem(
                title = "电量充足",
                detail = "当前电量 $soc%，续航 ${status.range ?: "--"}",
                level = HealthLevel.GOOD,
            )
        }

        // 电池温度
        val batteryTemp = cleanNumber(status.batteryTemp)
        items += when {
            batteryTemp == null -> HealthCheckItem(
                title = "电池温度",
                detail = "当前车况未上报电池温度",
                level = HealthLevel.GOOD,
            )
            batteryTemp <= BATTERY_TEMP_COLD -> HealthCheckItem(
                title = "电池温度过低",
                detail = "电池 ${batteryTemp.toInt()}℃，低温下充放电效率下降，建议开启电池预热",
                level = HealthLevel.WARNING,
                fixCommand = "batteryPreheat",
                fixLabel = "电池预热",
            )
            batteryTemp >= BATTERY_TEMP_HOT -> HealthCheckItem(
                title = "电池温度过高",
                detail = "电池 ${batteryTemp.toInt()}℃，建议停车降温后避免快充",
                level = HealthLevel.CRITICAL,
            )
            else -> HealthCheckItem(
                title = "电池温度正常",
                detail = "电池 ${batteryTemp.toInt()}℃，处于适宜工作区间",
                level = HealthLevel.GOOD,
            )
        }

        // 充放电回路（有读数才判断）
        val voltage = cleanNumber(status.chargeVoltage)
        val current = cleanNumber(status.chargeCurrent)
        if (voltage != null || current != null) {
            val parts = buildList {
                voltage?.let { add("${it.toInt()} V") }
                current?.let { add("${it}A") }
            }
            items += HealthCheckItem(
                title = "充放电回路",
                detail = "母线读数 ${parts.joinToString(" / ")}，链路通畅",
                level = HealthLevel.GOOD,
            )
        } else {
            items += HealthCheckItem(
                title = "充放电回路",
                detail = status.chargeCableConnected?.let {
                    if (it) "充电枪已连接，等待充电读数" else "未连接充电枪"
                } ?: "未上报充放电读数",
                level = HealthLevel.GOOD,
            )
        }

        // 累计里程
        status.totalMileage?.let {
            items += HealthCheckItem(
                title = "累计里程",
                detail = "已行驶 $it，建议按保养手册定期进店检查",
                level = HealthLevel.GOOD,
            )
        }

        return systemReport("动力与三电", items)
    }

    // ---------------- 2. 底盘与轮胎 ----------------

    private fun evaluateChassis(status: CarStatus): HealthSystemReport {
        val items = mutableListOf<HealthCheckItem>()
        val tires = status.tires

        if (tires.isEmpty()) {
            items += HealthCheckItem(
                title = "轮胎压力",
                detail = "当前车况未上报胎压数据，建议在车辆中控查看",
                level = HealthLevel.GOOD,
            )
        } else {
            tires.forEach { tire ->
                val pressure = cleanNumber(tire.pressure)
                val detail = buildString {
                    append("压力 ${tire.pressure ?: "--"}")
                    tire.temperature?.let { append("，温度 $it") }
                }
                items += when {
                    tire.warning -> HealthCheckItem(
                        title = "${tire.position}胎异常",
                        detail = "$detail，车端已上报胎压告警，请检查是否漏气",
                        level = HealthLevel.CRITICAL,
                    )
                    pressure == null -> HealthCheckItem(
                        title = "${tire.position}胎",
                        detail = "$detail（无压力数值，仅车端状态）",
                        level = HealthLevel.GOOD,
                    )
                    pressure <= TIRE_LOW -> HealthCheckItem(
                        title = "${tire.position}胎压力偏低",
                        detail = "$detail，低于 ${TIRE_LOW.toInt()} kPa 建议及时补气",
                        level = HealthLevel.WARNING,
                    )
                    pressure >= TIRE_HIGH -> HealthCheckItem(
                        title = "${tire.position}胎压力偏高",
                        detail = "$detail，高于 ${TIRE_HIGH.toInt()} kPa 建议适当放气",
                        level = HealthLevel.WARNING,
                    )
                    else -> HealthCheckItem(
                        title = "${tire.position}胎正常",
                        detail = detail,
                        level = HealthLevel.GOOD,
                    )
                }
            }
        }

        items += HealthCheckItem(
            title = "行驶状态",
            detail = status.drivingStatus?.let { "当前$it" } ?: "未上报行驶状态",
            level = HealthLevel.GOOD,
        )

        return systemReport("底盘与轮胎", items)
    }

    // ---------------- 3. 车身与门锁 ----------------

    private fun evaluateBody(status: CarStatus, capabilities: VehicleCapabilities): HealthSystemReport {
        val items = mutableListOf<HealthCheckItem>()

        // 车锁
        items += when (status.locked) {
            false -> HealthCheckItem(
                title = "车辆未上锁",
                detail = "车门处于解锁状态，建议远程上锁",
                level = HealthLevel.WARNING,
                fixCommand = "lock",
                fixLabel = "远程上锁",
            )
            true -> HealthCheckItem(
                title = "车辆已上锁",
                detail = "整车处于设防状态",
                level = HealthLevel.GOOD,
            )
            null -> HealthCheckItem(
                title = "车锁状态未知",
                detail = "未收到锁车信号",
                level = HealthLevel.GOOD,
            )
        }

        // 四门与前舱盖
        val doors = status.doors
        val openDoors = doors?.openLabels.orEmpty()
        if (openDoors.isNotEmpty()) {
            items += HealthCheckItem(
                title = "车门未关好",
                detail = "${openDoors.joinToString("、")}未关严，需回到车旁手动关闭；车辆通常不支持远程关门",
                level = HealthLevel.CRITICAL,
            )
        } else if (doors?.hasAnySignal == true) {
            items += HealthCheckItem(
                title = "四门与舱盖",
                detail = "全部关闭到位",
                level = HealthLevel.GOOD,
            )
        } else {
            items += HealthCheckItem(
                title = "四门与舱盖",
                detail = "当前车况未上报门状态",
                level = HealthLevel.GOOD,
            )
        }

        // 后备箱 / 前备箱
        status.trunkOpen?.let { open ->
            items += if (open) HealthCheckItem(
                title = "后备箱未关",
                detail = "后备箱处于开启状态，关好后锁车更安全",
                level = HealthLevel.WARNING,
                fixCommand = ControlActions.resolveCommand("trunk", status),
                fixLabel = "关闭后备箱",
            ) else HealthCheckItem(
                title = "后备箱",
                detail = "已关闭",
                level = HealthLevel.GOOD,
            )
        }
        status.frunkOpen?.let { open ->
            items += if (open) HealthCheckItem(
                title = "前备箱未关",
                detail = "前备箱处于开启状态",
                level = HealthLevel.WARNING,
                fixCommand = ControlActions.resolveCommand("frunk", status),
                fixLabel = "关闭前备箱",
            ) else HealthCheckItem(
                title = "前备箱",
                detail = "已关闭",
                level = HealthLevel.GOOD,
            )
        }

        // 车窗 / 天窗 / 遮阳帘
        status.windowOpen?.let { open ->
            items += if (open) HealthCheckItem(
                title = "车窗未关",
                detail = "有车窗处于开启状态，遇雨可能进水",
                level = HealthLevel.WARNING,
                fixCommand = ControlActions.resolveCommand("window", status),
                fixLabel = "关闭车窗",
            ) else HealthCheckItem(
                title = "车窗",
                detail = "全部关闭",
                level = HealthLevel.GOOD,
            )
        }
        // 天窗：仅在车辆确实配备可开启天窗时展示。未选装车网关仍会下发 1718 的 SNA 占位，
        // 能力识别阶段已用值域判定将其排除（capabilities.sunroof = false），此处据此隐藏。
        if (capabilities.sunroof) {
            status.sunroofOpen?.let { open ->
                items += if (open) HealthCheckItem(
                    title = "天窗未关",
                    detail = "天窗处于开启状态",
                    level = HealthLevel.WARNING,
                    fixCommand = ControlActions.resolveCommand("sunroof", status),
                    fixLabel = "关闭天窗",
                ) else HealthCheckItem(
                    title = "天窗",
                    detail = "已关闭",
                    level = HealthLevel.GOOD,
                )
            }
        }
        status.sunshadeOpen?.let { open ->
            items += HealthCheckItem(
                title = "遮阳帘",
                detail = if (open) "遮阳帘已打开" else "遮阳帘已关闭",
                level = HealthLevel.GOOD,
            )
        }

        // 哨兵模式：仅在车辆具备哨兵能力时展示（无该硬件的车网关不下发信号，能力识别为 false）。
        if (capabilities.sentry) {
            items += when (status.sentryOn) {
                true -> HealthCheckItem(
                    title = "哨兵模式",
                    detail = "已开启，停车期间可记录异常",
                    level = HealthLevel.GOOD,
                )
                false -> HealthCheckItem(
                    title = "哨兵模式未开启",
                    detail = "建议在陌生或露天停车场地开启哨兵模式",
                    level = HealthLevel.WARNING,
                    fixCommand = ControlActions.resolveCommand("sentry", status),
                    fixLabel = "开启哨兵",
                )
                null -> HealthCheckItem(
                    title = "哨兵模式",
                    detail = "当前车况未上报哨兵状态",
                    level = HealthLevel.GOOD,
                )
            }
        }

        return systemReport("车身与门锁", items)
    }

    // ---------------- 4. 空调与舒适 ----------------

    private fun evaluateClimate(status: CarStatus): HealthSystemReport {
        val items = mutableListOf<HealthCheckItem>()

        items += when (status.acOn) {
            true -> HealthCheckItem(
                title = "空调运行中",
                detail = "目标温度 ${status.acTargetTemp ?: "--"}，风量 ${status.acWindLevel ?: "--"}，" +
                    "循环 ${status.acCircle ?: "--"}",
                level = HealthLevel.GOOD,
                fixCommand = "acOff",
                fixLabel = "关闭空调",
            )
            false -> HealthCheckItem(
                title = "空调已关闭",
                detail = "长时间停放建议保持关闭以节省电量",
                level = HealthLevel.GOOD,
            )
            null -> HealthCheckItem(
                title = "空调状态",
                detail = "未上报空调开关信号",
                level = HealthLevel.GOOD,
            )
        }

        // 车内温度
        val interior = cleanNumber(status.interiorTemp)
        if (interior != null) {
            items += if (interior >= INTERIOR_TEMP_HOT) {
                HealthCheckItem(
                    title = "车内温度偏高",
                    detail = "车内 ${interior.toInt()}℃，建议远程通风降温后再上车",
                    level = HealthLevel.WARNING,
                    fixCommand = ControlActions.resolveCommand("window", status),
                    fixLabel = "开窗通风",
                )
            } else {
                HealthCheckItem(
                    title = "车内温度",
                    detail = "车内 ${interior.toInt()}℃，处于舒适区间",
                    level = HealthLevel.GOOD,
                )
            }
        }

        // 冰箱：驻车时若仍开着，会持续耗电
        val fridge = status.fridge
        if (fridge?.enabled == true) {
            items += if (status.soc <= LOW_SOC) {
                HealthCheckItem(
                    title = "车载冰箱耗电",
                    detail = "冰箱仍在运行，而电量仅 ${status.soc}%，建议关闭以保留续航",
                    level = HealthLevel.WARNING,
                    fixCommand = "fridgeOff",
                    fixLabel = "关闭冰箱",
                )
            } else {
                HealthCheckItem(
                    title = "车载冰箱",
                    detail = "运行中，注意持续耗电",
                    level = HealthLevel.GOOD,
                    fixCommand = "fridgeOff",
                    fixLabel = "关闭冰箱",
                )
            }
        }

        // 座椅 / 方向盘 / 后视镜加热：驻车长期开着同样耗电
        val activeHeating = buildList {
            status.seatHeating?.driver?.takeIf { it > 0 }?.let { add("主驾座椅加热") }
            status.seatHeating?.passenger?.takeIf { it > 0 }?.let { add("副驾座椅加热") }
            status.seatHeating?.leftRear?.takeIf { it > 0 }?.let { add("左后座椅加热") }
            status.seatHeating?.rightRear?.takeIf { it > 0 }?.let { add("右后座椅加热") }
            status.steeringWheelHeating?.takeIf { it > 0 }?.let { add("方向盘加热") }
            status.rearviewMirrorHeating?.takeIf { it }?.let { add("后视镜加热") }
        }
        if (activeHeating.isNotEmpty()) {
            items += HealthCheckItem(
                title = "加热功能运行中",
                detail = "${activeHeating.joinToString("、")}正在工作，驻车时会持续耗电",
                level = HealthLevel.GOOD,
            )
        }

        return systemReport("空调与舒适", items)
    }

    // ---------------- 工具 ----------------

    private fun systemReport(name: String, items: List<HealthCheckItem>): HealthSystemReport {
        val level = HealthLevel.worst(items.map { it.level })
        val count = items.count { it.level != HealthLevel.GOOD }
        val statusText = when {
            level == HealthLevel.CRITICAL -> "$count 项严重"
            level == HealthLevel.WARNING -> "$count 项提醒"
            else -> "正常"
        }
        return HealthSystemReport(
            name = name,
            statusText = statusText,
            level = level,
            items = items,
        )
    }

    /** 从带单位的文本里取出数值，例如 "245 kPa" → 245.0，"28 °C" → 28.0。 */
    private fun cleanNumber(raw: String?): Double? {
        val text = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        return numberRegex.find(text)?.value?.toDoubleOrNull()
    }

    private val numberRegex = Regex("-?\\d+(\\.\\d+)?")
}
