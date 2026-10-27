package com.leapcontrol.app.ui

import com.leapcontrol.app.R

/**
 * 内置卡通头像目录（20 款）。
 *
 * 这些图是同一套提示词模板生成的：扁平矢量插画、单色圆底、无描边、
 * 头肩构图。按需求覆盖了不同性别、不同年龄与不同性格：
 * 青春活泼（1/2/6/10/17）、可爱萌系（3/7/12）、
 * 成熟稳重（4/5/8/13/16/19/20）、文艺温和（9/11/15）、中性潮酷（14/18）。
 *
 * 资源放在 `drawable-nodpi`（256×256 WebP，圆形已带 alpha），
 * 只用于「我的」页头像与头像选择器，随包体积约 196KB。
 */
object AvatarCatalog {

    val presets: List<Int> = listOf(
        R.drawable.avatar_01, // 青春活泼 · 男生
        R.drawable.avatar_02, // 青春活泼 · 高马尾女生
        R.drawable.avatar_03, // 可爱 · 丸子头女生
        R.drawable.avatar_04, // 成熟稳重 · 眼镜男士
        R.drawable.avatar_05, // 成熟知性 · 低马尾女生
        R.drawable.avatar_06, // 阳光运动 · 反戴棒球帽
        R.drawable.avatar_07, // 可爱 · 卷发男生
        R.drawable.avatar_08, // 成熟稳重 · 络腮胡
        R.drawable.avatar_09, // 温柔 · 长卷发女生
        R.drawable.avatar_10, // 阳光运动 · 运动发带
        R.drawable.avatar_11, // 文艺 · 齐刘海圆框眼镜
        R.drawable.avatar_12, // 可爱 · 双马尾星星发夹
        R.drawable.avatar_13, // 干练 · 短波波头
        R.drawable.avatar_14, // 潮酷 · 卷发耳环
        R.drawable.avatar_15, // 温和 · 中长发胡茬
        R.drawable.avatar_16, // 知性 · 低盘发
        R.drawable.avatar_17, // 少年感 · 红棕碎发
        R.drawable.avatar_18, // 中性 · 短发
        R.drawable.avatar_19, // 稳重 · 花白短发
        R.drawable.avatar_20, // 优雅 · 银灰短发
    )

    /** 默认头像：「青春活泼 · 男生」，视觉上最中性、最不会有人觉得被冒犯。 */
    const val DEFAULT_INDEX = 0

    val count: Int get() = presets.size

    fun drawableAt(index: Int): Int = presets[index.coerceIn(0, presets.lastIndex)]
}

/**
 * 头像选择的持久化编解码。
 *
 * 用可读字符串存进偏好，好处是出问题能一眼看出存的是什么：
 * - `preset:<下标>` 内置头像
 * - `custom`        用户从相册挑的图（文件在 `filesDir/profile_avatar.png`）
 *
 * 任何解析不了的值（旧版本残留、越界下标、手改成乱码）一律回落到默认头像，
 * 绝不抛异常——头像坏了不该让「我的」页白屏。
 */
sealed interface AvatarChoice {

    data class Preset(val index: Int) : AvatarChoice

    data object Custom : AvatarChoice

    companion object {
        const val CUSTOM_RAW = "custom"

        /** 首次进入、或选择被重置时用的值：第一个内置头像。 */
        const val DEFAULT_RAW = "preset:0"

        fun preset(index: Int): String = "preset:$index"

        fun encode(choice: AvatarChoice): String = when (choice) {
            is Preset -> preset(choice.index)
            Custom -> CUSTOM_RAW
        }

        fun decode(raw: String?, count: Int = AvatarCatalog.count): AvatarChoice {
            val text = raw?.trim().orEmpty()
            if (text == CUSTOM_RAW) return Custom
            if (!text.startsWith("preset:")) return Preset(AvatarCatalog.DEFAULT_INDEX)
            val index = text.removePrefix("preset:").toIntOrNull()
                ?: return Preset(AvatarCatalog.DEFAULT_INDEX)
            // 下标越界（比如以后精简了头像数目）也回落到默认，别让它崩在 drawableAt
            if (index !in 0 until count) return Preset(AvatarCatalog.DEFAULT_INDEX)
            return Preset(index)
        }
    }
}
