package com.leapbuddy.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Curtains
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Window
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 首页控车按钮。
 *
 * [id] 若为 [toggle] 复合按钮（后备箱 / 车窗 / 遮阳帘），不直接对应指令，
 * 需经 [ControlActions.resolveCommand] 按当前车况解析成开或关；
 * 其余 id 与 Commands.build(name) 的指令名一一对应。
 */
data class ControlAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** 是否按车况高亮，并在开启时下发关闭指令、关闭时下发开启指令。 */
    val toggle: Boolean = false,
)

object ControlActions {
    val all: List<ControlAction> = listOf(
        ControlAction("unlock", "解锁", Icons.Filled.LockOpen),
        ControlAction("lock", "上锁", Icons.Filled.Lock),
        ControlAction("trunk", "后备箱", Icons.Filled.Luggage, toggle = true),
        ControlAction("window", "车窗", Icons.Filled.Window, toggle = true),
        ControlAction("horn", "鸣笛寻车", Icons.Filled.NotificationsActive),
        ControlAction("batteryPreheat", "电池预热", Icons.Filled.LocalFireDepartment),
        ControlAction("sunshade", "遮阳帘", Icons.Filled.Curtains, toggle = true),
        ControlAction("sunroof", "天窗", Icons.Filled.Roofing, toggle = true),
        ControlAction("frunk", "前备箱", Icons.Filled.SensorDoor, toggle = true),
        ControlAction("sentry", "哨兵", Icons.Filled.Security, toggle = true),
    )

    val defaultLayout: List<String> = all.map { it.id }

    fun find(id: String): ControlAction? = all.firstOrNull { it.id == id }

    /** 复合按钮是否处于「开启」状态（null 表示车况未知，不高亮）。 */
    fun isActive(id: String, status: CarStatus?): Boolean? = when (id) {
        "trunk" -> status?.trunkOpen
        "window" -> status?.windowOpen
        "sunshade" -> status?.sunshadeOpen
        "sunroof" -> status?.sunroofOpen
        "frunk" -> status?.frunkOpen
        "sentry" -> status?.sentryOn
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
        else -> id
    }

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
