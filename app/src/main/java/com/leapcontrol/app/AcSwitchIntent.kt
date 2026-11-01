package com.leapcontrol.app

/**
 * 首页空调开关的「本地意图」。
 *
 * ## 为什么需要它
 * 开关的显示状态原来**完全由车况决定**（`status.acOn`）。但车端 acSwitch 上报滞后实测 2~5 秒，
 * 下发指令本身又要等回执，于是出现两个连锁反应：
 * 1. 点下去界面毫无变化，用户以为没生效，接着再点；
 * 2. 等指令结束、意图熄灭后，如果车况还没回报，开关会**弹回原状态**，
 *    用户就会按着一个过时的状态继续点，方向因此错乱（想关却一直发开）。
 *
 * 所以点下去就地记一个意图，界面先按意图显示（乐观更新），等车端回报了同一状态
 * 再交还给车况；车端始终不认就 [GIVE_UP_MS] 后放弃，老老实实显示车端状态 ——
 * 宁可承认失败，也不要一直骗用户「已经关了」。
 *
 * 这里只放判定，不碰 UI 与网络，纯函数好写单测。
 */
object AcSwitchIntent {

    /**
     * 意图最多压制车况多久。
     *
     * 取值要盖住「下发 + 车端上报」的完整链路（实测 2~6 秒、慢的时候 10 秒左右），
     * 又不能长到让一次真失败的点击长期显示成成功。15 秒是这两者的折中：
     * 超时后回到车端真实状态，用户看到开关弹回去，就知道这次没成功。
     */
    const val GIVE_UP_MS = 15_000L

    /** 界面上该显示成开还是关：有意图先按意图（点下去立刻跟手），没有才看车端。 */
    fun shown(remote: Boolean?, intent: Boolean?): Boolean = intent ?: (remote == true)

    /**
     * 收敛循环的下一跳：返回还需要下发的目标状态，null 表示不用再发。
     *
     * [lastSent] 是这一轮已经发出去的那一条。两者相同就不再重发 ——
     * 用户在等待期间反复点同一个方向，只会更新意图，不会打出一串重复指令。
     */
    fun nextTarget(intent: Boolean?, lastSent: Boolean?): Boolean? {
        val target = intent ?: return null
        return if (target == lastSent) null else target
    }

    /**
     * 意图还要不要保留（保留 = 界面继续按意图显示）。
     *
     * 三条规则：
     * - 车端回报了同一状态 → 指令已生效，交还给车况；
     * - 超过 [GIVE_UP_MS] 仍没等到 → 放弃，以车端为准；
     * - 其余情况（含车端读不到、或还报着旧值）→ 继续按意图显示，
     *   否则「点了立刻弹回去」比慢半拍更让人以为按坏了。
     */
    fun keep(remote: Boolean?, intent: Boolean?, msSinceIntent: Long): Boolean? {
        if (intent == null) return null
        if (remote == intent) return null
        if (msSinceIntent >= GIVE_UP_MS) return null
        return intent
    }
}
