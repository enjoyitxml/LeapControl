package com.leapcontrol.app

/**
 * 首页「快速切换壁纸 / 背景」的三种状态（设置 → 首页）。
 *
 * 首页有两块可以换图的部件：**车模**（换的是主题壁纸）和**相伴卡**（换的是卡片背景）。
 * 这个设置只决定「用什么手势触发」，不改变换的范围——车模仍然只在当前明暗对应的池子里换，
 * 相伴卡仍然只在 6 套内置背景里换。
 *
 *  - [DOT] 小白点：两块右上角各挂一颗呼吸白点，点白点换图；两颗点可以分别关掉
 *    （有的人只想要车模那颗，不想要相伴卡那颗）。
 *  - [TAP] 点击切换：不显示白点，直接点车模 / 相伴卡本体就换一张。画面更干净，
 *    代价是没有可见提示，属于「知道的人自己用」。
 *  - [OFF] 关闭：首页不给快捷入口，只能去个性化页手动选。适合固定一套壁纸、不想被误触的人。
 */
enum class QuickSwitchMode(
    val label: String,
    /** 该模式的触发方式说明：卡片下面的提示行直接用这句，别在 UI 里另写一份 */
    val desc: String,
) {
    DOT(
        "小白点",
        "两块右上角各挂一颗会呼吸的小白点，点白点换一张。下面可以只留其中一个。",
    ),
    TAP(
        "点击切换",
        "不显示小白点，直接点车模或相伴卡本体就换一张。画面更干净，但没有可见提示。",
    ),
    OFF(
        "关闭",
        "首页不提供快捷切换，换壁纸请到「个性化」里手动选。适合固定一套、不想被误触的情况。",
    );

    companion object {
        /** 认不出的值一律退回 [DOT]（默认形态，用户最容易发现功能还在）。 */
        fun fromPreference(raw: String?): QuickSwitchMode =
            entries.firstOrNull { it.name == raw } ?: DOT

        /**
         * 兼容上一版那个布尔开关（`quick_dot_enabled`）。
         *
         * 旧版只有「开 / 关」两种：开 = 两颗点都显示，关 = 都隐藏并且没有任何快捷入口。
         * 所以关过的人应该落到 [OFF]，而不是升级后突然又多出一颗点。
         * 新键存在时以新键为准（用户升级后改过设置的情况）。
         */
        fun fromLegacy(rawMode: String?, legacyDotEnabled: Boolean?): QuickSwitchMode =
            if (rawMode != null) fromPreference(rawMode)
            else if (legacyDotEnabled == false) OFF
            else DOT
    }
}

/**
 * 单个部件（车模 / 相伴卡）当前该怎么呈现快捷切换。
 *
 * UI 只认这三个值，不去分辨「模式是什么、这颗点开没开」——把两层规则收敛在这里，
 * 两处按钮就不会各自算出不一样的结果。
 */
enum class QuickTarget {
    /** 不提供：不显示白点，部件本身也不可点 */
    NONE,

    /** 显示呼吸小白点，点白点触发 */
    DOT,

    /** 不显示白点，点部件本体触发 */
    TAP,
}

/**
 * 快捷切换的三项偏好的不可变快照。
 *
 * 合成一个对象而不是三个独立的 State：UI 只订阅一次，也不会出现
 * 「模式已经变成小白点、单部件开关还是上一帧」这种中间态。
 *
 * [carDot] / [cardDot] 只在 [QuickSwitchMode.DOT] 下有意义——[QuickSwitchMode.TAP]
 * 是整块可点，没有"某一块不参与"的概念。
 */
data class QuickSwitchPrefs(
    val mode: QuickSwitchMode = QuickSwitchMode.DOT,
    /** 车模右上角的小白点（换主题壁纸）是否显示 */
    val carDot: Boolean = true,
    /** 相伴卡右上角的小白点（换卡片背景）是否显示 */
    val cardDot: Boolean = true,
) {
    val carTarget: QuickTarget get() = target(carDot)
    val cardTarget: QuickTarget get() = target(cardDot)

    private fun target(dotEnabled: Boolean): QuickTarget = when (mode) {
        QuickSwitchMode.DOT -> if (dotEnabled) QuickTarget.DOT else QuickTarget.NONE
        QuickSwitchMode.TAP -> QuickTarget.TAP
        QuickSwitchMode.OFF -> QuickTarget.NONE
    }

    /**
     * 实际生效的呈现方式 —— 在用户偏好之上再叠一层「这块正在用用户自己的图」的临时锁定。
     *
     * **为什么要有这一层**：自定义壁纸 / 自定义背景是用户从相册里翻出来、又亲手拖过缩放框的图，
     * 而首页那两颗小白点（或整块可点）就在图上，一次误触就会被随机换成内置主题，
     * 而且换掉的图**找不回来**（只能重新去相册里挑）。
     * 所以只要这一块正在显示用户自己的图，[QuickTarget.NONE] 一律压掉，不给入口。
     *
     * **只改生效结果，不动 [QuickSwitchPrefs]**：用户没关过这个功能，
     * 等他切回内置壁纸 / 内置背景，入口应该自己回来，不需要再去设置里重新打开一次。
     *
     * @param customLocked 这一块此刻是否正在显示用户自己的图
     */
    fun effectiveTarget(part: QuickPart, customLocked: Boolean): QuickTarget =
        if (customLocked) {
            QuickTarget.NONE
        } else {
            when (part) {
                QuickPart.CAR -> carTarget
                QuickPart.CARD -> cardTarget
            }
        }
}

/** 首页两块可以快捷换图的部件，[CAR] 换主题壁纸，[CARD] 换相伴卡背景。 */
enum class QuickPart { CAR, CARD }

/**
 * 自定义图生效时给用户看的几句话。
 *
 * 集中放一处：个性化页（选图的地方）和设置页（发现小白点不见了的地方）说的是同一件事，
 * 两处各写一份迟早会写成两种说法。
 */
object QuickSwitchLock {
    /** 个性化页 · 相伴卡：自定义背景正在使用时 */
    const val BACKDROP = "为避免误触换掉这张图，相伴卡的随机换背景入口已自动收起。"

    /** 个性化页 · 主题：自定义壁纸正在使用时 */
    const val WALLPAPER = "为避免误触换掉这张图，首页的随机换壁纸入口（小白点 / 点车模）已自动收起。"

    /** 个性化页 · 主题：选图建议（只要传过图就显示，和有没有选中无关） */
    const val PICK_ADVICE =
        "挑图建议：把画面主体放在上半屏、下半屏尽量留空。" +
            "首页的车模和卡片都压在下半屏，主体放低了会被挡住。"

    /** 设置页：小白点开关明明开着，首页却没有点，得有个解释（并指明是哪几块被收起） */
    fun settings(parts: List<String>): String =
        "已自动收起${parts.joinToString("、")}的随机切换，避免误触把图换掉；要换图请到「个性化」里选。"

    /** [settings] 里用的部件名 */
    const val PART_CAR = "车模（自定义壁纸）"
    const val PART_CARD = "相伴卡（自定义背景）"
}
