package com.leapcontrol.app.ui

import androidx.annotation.DrawableRes
import com.leapcontrol.app.R

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

    val ALL: List<OwnershipBackdrop> = listOf(
        OwnershipBackdrop("mountain", "雪山", R.drawable.bg_ownership_mountain, 0.30f),
        OwnershipBackdrop("grassland", "草原", R.drawable.bg_ownership_grassland, 0.58f),
        OwnershipBackdrop("seaside", "海岸", R.drawable.bg_ownership_seaside, 0.58f),
        OwnershipBackdrop("autumn", "秋林", R.drawable.bg_ownership_autumn, 0.42f),
        OwnershipBackdrop("desert", "沙漠", R.drawable.bg_ownership_desert, 0.46f),
        OwnershipBackdrop("city", "夜色", R.drawable.bg_ownership_city, 0.22f),
    )

    /** 认不出的 id 一律退回默认那张，避免升级后旧配置导致空白卡。 */
    fun byId(id: String?): OwnershipBackdrop =
        ALL.firstOrNull { it.id == id } ?: ALL.first()
}
