package com.leapcontrol.app.ui

import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleCapabilities

/**
 * 首页控车按钮。
 *
 * [id] 若为 [toggle] 复合按钮（后备箱 / 车窗 / 遮阳帘），不直接对应指令，
 * 需经 [ControlActions.resolveCommand] 按当前车况解析成开或关；
 * 其余 id 与 Commands.build(name) 的指令名一一对应。
 *
 * [available] 描述该按钮依赖的硬件配置：车不支持时（[VehicleCapabilities] 对应位为 false）
 * 按钮不会出现，避免给出点了必然失败的入口。默认恒可用。
 */
data class ControlAction(
    val id: String,
    val label: String,
    /** 图标：与底部导航同一套自绘 drawable（1024 视口实心风格）。 */
    val iconRes: Int,
    /**
     * 开 / 关一体的复合按钮（后备箱、车窗、遮阳帘…）。
     *
     * 以前用它决定「要不要按车况高亮」，现在亮灭统一由 [ControlActions.isActive] 判断
     * （解锁和电池预热也要高亮，不能只看这个标记），它只剩「说明这颗按钮是双向的」这条语义。
     */
    val toggle: Boolean = false,
    val available: (VehicleCapabilities) -> Boolean = { true },
)

object ControlActions {
    val all: List<ControlAction> = listOf(
        ControlAction("unlock", "解锁", R.drawable.ic_qc_unlock),
        ControlAction("lock", "上锁", R.drawable.ic_qc_lock),
        ControlAction("trunk", "后备箱", R.drawable.ic_qc_trunk, toggle = true),
        ControlAction("window", "车窗", R.drawable.ic_qc_window, toggle = true),
        ControlAction("horn", "鸣笛寻车", R.drawable.ic_qc_horn),
        ControlAction("batteryPreheat", "电池预热", R.drawable.ic_qc_preheat),
        ControlAction("sunshade", "遮阳帘", R.drawable.ic_qc_sunshade, toggle = true),
        ControlAction("sunroof", "天窗", R.drawable.ic_qc_sunroof, toggle = true, available = { it.sunroof }),
        ControlAction("frunk", "前备箱", R.drawable.ic_qc_frunk, toggle = true, available = { it.frunk }),
        ControlAction("sentry", "哨兵", R.drawable.ic_qc_sentry, toggle = true, available = { it.sentry }),
    )

    val defaultLayout: List<String> = all.map { it.id }

    fun find(id: String): ControlAction? = all.firstOrNull { it.id == id }

    /**
     * 按车辆配置过滤出可用的按钮，顺序沿用给定的布局。
     *
     * @param capabilities 已识别的车辆配置；传 null（车况与配置都还没拿到）时
     *                     只保留不依赖硬件的按钮——宁可少给，也不给点了必然报错的入口。
     */
    fun available(capabilities: VehicleCapabilities?, saved: List<String>): List<ControlAction> {
        val caps = capabilities ?: VehicleCapabilities.EMPTY
        val ordered = saved.mapNotNull { id -> find(id) }
        return ordered.filter { it.available(caps) }
    }

    /**
     * 这颗按钮此刻该不该高亮（null = 车况未知，不高亮，也不猜）。
     *
     * 用户 2026-11-05 定稿：
     * - **解锁**：车没上锁时亮着 —— 看到解锁键亮，就知道车现在没锁；
     * - **后备箱 / 车窗 / 遮阳帘 / 天窗 / 前备箱 / 哨兵 / 电池预热**：功能开着就亮，
     *   亮着点=关、灭着点=开（电池预热看 [CarStatus.batteryPreheatOn]）；
     * - **上锁 / 鸣笛**：单次动作，点完就结束，没有「开着的状态」可亮。
     */
    fun isActive(id: String, status: CarStatus?): Boolean? = when (id) {
        "unlock" -> status?.locked?.let { !it }
        "batteryPreheat" -> status?.batteryPreheatOn
        "trunk" -> status?.trunkOpen
        "window" -> status?.windowOpen
        "sunshade" -> status?.sunshadeOpen
        "sunroof" -> status?.sunroofOpen
        "frunk" -> status?.frunkOpen
        "sentry" -> status?.sentryOn
        // 上锁 / 鸣笛是单次动作：高亮只会被误读成「这个功能开着」
        else -> null
    }

    /**
     * 解析本次点击真正下发的指令。
     * 复合按钮按车况取反；车况未知时取「开」的保守方向，
     * 由车端自身状态决定实际效果，不会误关已关闭的部件。
     */
    fun resolveCommand(id: String, status: CarStatus?): String = when (id) {
        "trunk" -> if (status?.trunkOpen == true) "trunkClose" else "trunkOpen"
        "window" -> if (status?.windowOpen == true) "windowClose" else "windowOpen"
        "sunshade" -> if (status?.sunshadeOpen == true) "sunshadeClose" else "sunshadeOpen"
        "sunroof" -> if (status?.sunroofOpen == true) "sunroofClose" else "sunroofOpen"
        "frunk" -> if (status?.frunkOpen == true) "frunkClose" else "frunkOpen"
        "sentry" -> if (status?.sentryOn == true) "sentryOff" else "sentryOn"
        // 预热同样是开/关一体的：亮着点就是关（热着再发一次「开」等于没点）
        "batteryPreheat" -> if (status?.batteryPreheatOn == true) "batteryPreheatOff" else "batteryPreheat"
        else -> id
    }

    /**
     * 二次确认弹窗要显示的**具体指令**，必须是「打开 X / 关闭 X」这样的动宾短语，
     * 而不是按钮上那个功能名（「遮阳帘」不是一条指令，2026-11-06 用户反馈）。
     *
     * 早期版本遮阳帘是「打开遮阳帘 / 关闭遮阳帘」两个独立入口，文案自带动作；
     * 合并成一颗按车况取反的按钮后，弹窗一直传 `action.label`，于是退化成只报功能名。
     *
     * 调用方必须先 [resolveCommand]，再把**同一个指令名**同时交给本函数和实际下发，
     * 保证「弹窗说的」与「真正发出去的」不会因弹窗期间车况刷新而错位。
     *
     * 这里不复用 `ConfirmAction.title`：那份表只覆盖小组件 / 磁贴「再点一次」的 12 条，
     * 缺天窗、前备箱、哨兵和预热关闭，且「电池预热」不分开关。
     */
    fun commandLabel(command: String): String = when (command) {
        "lock" -> "上锁车辆"
        "unlock" -> "解锁车辆"
        "horn" -> "鸣笛寻车"
        "trunkOpen" -> "打开后备箱"
        "trunkClose" -> "关闭后备箱"
        "windowOpen" -> "打开车窗"
        "windowClose" -> "关闭车窗"
        "sunshadeOpen" -> "打开遮阳帘"
        "sunshadeClose" -> "关闭遮阳帘"
        "sunroofOpen" -> "打开天窗"
        "sunroofClose" -> "关闭天窗"
        "frunkOpen" -> "打开前备箱"
        "frunkClose" -> "关闭前备箱"
        "sentryOn" -> "开启哨兵"
        "sentryOff" -> "关闭哨兵"
        "batteryPreheat" -> "开启电池预热"
        "batteryPreheatOff" -> "关闭电池预热"
        // 兜底：不认识就退回按钮名，绝不把内部指令名（sunshadeOpen）糊给用户
        else -> find(command)?.label ?: "控车"
    }

    /** 按钮 id + 当前车况 → 弹窗文案；与 [resolveCommand] 共用同一份开/关判定。 */
    fun confirmLabel(id: String, status: CarStatus?): String = commandLabel(resolveCommand(id, status))

    /** 解析已保存的顺序，过滤掉已下线指令并保证最少 3 个。 */
    fun sanitize(saved: List<String>): List<String> {
        val valid = saved.filter { id -> all.any { it.id == id } }.distinct()
        return when {
            valid.size >= 3 -> valid.take(MaxSelectable)
            else -> defaultLayout
        }
    }

    /** 编辑按钮固定占最后一格，因此最多可选 7 个（双行 8 格）。 */
    const val MaxSelectable = 7
    const val MinSelectable = 3
}
