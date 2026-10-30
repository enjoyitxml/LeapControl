package com.leapcontrol.app.ui

import androidx.annotation.DrawableRes
import com.leapcontrol.app.R
import kotlin.random.Random

/**
 * 「与爱车相伴」卡的内置背景。
 *
 * 每张图都是「上半天空 + 下半山峦/水面/城市」的构图，卡片按 3:1 裁中间带后，
 * 左上角落在天空区，所以文字始终压在最干净的一块上。
 *
 * [scrim] 是左侧压暗强度：天空越亮越白（草原/海岸那种），压得越重，白字才站得住；
 * 夜色那张本身就深，压太多会把灯光糊掉，所以只给 0.22。
 */
data class OwnershipBackdrop(
    val id: String,
    val label: String,
    @DrawableRes val res: Int,
    val scrim: Float,
)

object OwnershipBackdrops {

    const val DEFAULT_ID = "mountain"

    /**
     * 「自定义」背景的 id。
     *
     * 它不在 [ALL] 里：那 6 张是内置 drawable，随机换背景、byId 兜底都只认内置；
     * 自定义是一张用户上传的位图（见 `CustomBackdropStore`），单独一条路。
     * 真要用到 [byId] 时返回下面的 [CUSTOM_PLACEHOLDER]，图片缺失也不会崩。
     */
    const val CUSTOM_ID = "custom"

    val ALL: List<OwnershipBackdrop> = listOf(
        OwnershipBackdrop("mountain", "雪山", R.drawable.bg_ownership_mountain, 0.30f),
        OwnershipBackdrop("grassland", "草原", R.drawable.bg_ownership_grassland, 0.58f),
        OwnershipBackdrop("seaside", "海岸", R.drawable.bg_ownership_seaside, 0.58f),
        OwnershipBackdrop("autumn", "秋林", R.drawable.bg_ownership_autumn, 0.42f),
        OwnershipBackdrop("desert", "沙漠", R.drawable.bg_ownership_desert, 0.46f),
        OwnershipBackdrop("city", "夜色", R.drawable.bg_ownership_city, 0.22f),
    )

    /**
     * 自定义背景的占位配置。
     *
     * 只在「id 是 custom 但图读不出来」时用到 —— 回落到雪山那张，卡片不至于空一块。
     * 压暗强度按最坏情况给（用户拍的图可能很亮），保证白字站得住。
     */
    private val CUSTOM_PLACEHOLDER =
        OwnershipBackdrop(CUSTOM_ID, "自定义", R.drawable.bg_ownership_mountain, 0.42f)

    /** 认不出的 id 一律退回默认那张，避免升级后旧配置导致空白卡。 */
    fun byId(id: String?): OwnershipBackdrop = when (id) {
        CUSTOM_ID -> CUSTOM_PLACEHOLDER
        else -> ALL.firstOrNull { it.id == id } ?: ALL.first()
    }

    /**
     * 相伴卡右上角小白点：随机换一张与当前**不同**的背景。
     *
     * 与主题那套随机是两件事，互不影响——点这张卡上的点，只换这张卡的图，
     * 不动外观模式、不动主题、也不动日间/夜间两个槽位。
     *
     * 池子里只有一张时（现在有 6 张，属于兜底）原地返回，不会因为随机到自己而报错。
     */
    fun randomOther(currentId: String?, random: Random = Random.Default): String {
        val current = byId(currentId).id
        return pickOtherId(ALL.map { it.id }, current, random) ?: current
    }
}

/**
 * 从 [pool] 里随机挑一个不等于 [current] 的 id。
 *
 * 抽出来是为了能脱离 drawable 资源做单测（[OwnershipBackdrops.ALL] 里带 @DrawableRes）。
 * 剔除当前值这一步是刻意的：点一下必须能看出变化，抽到"还是这张"等于没点。
 * 去重后为空（池子里只有当前这一张）时返回 null，由调用方决定原地不动。
 */
internal fun pickOtherId(pool: List<String>, current: String?, random: Random): String? {
    val candidates = pool.filter { it != current }.ifEmpty { return null }
    return candidates[random.nextInt(candidates.size)]
}
