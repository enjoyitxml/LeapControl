package com.leapcontrol.app.ui

import androidx.compose.ui.graphics.Color

/**
 * 背景主题。
 *
 * 共 40 套：日间 20 套、夜间 20 套，各含 **10 套静态 + 10 套动态**。
 *
 * 每一套 = 整页基础渐变 + 上半屏的场景画面（[scene]，由 [paintScene] 实时绘制）+
 * 一个主题色（[accent]，作用于卡片底色、柱状图、强调元素）。
 *
 * 全部是实时绘制，**不含任何图片或视频**：静态场景画一次，动态场景只把「时间进度 t」
 * 当参数重绘。场景画面上限固定在上半屏，并做了两重衰减（场景自身淡出 + 顶部雾化），
 * 所以车名 / 续航 / 车模 / 快捷控车都压在很淡的画面上，不会被「糊住」。
 */
data class SeasonTheme(
    val id: String,
    val label: String,
    /** 一句话说明，选择器里做副标题 */
    val mood: String,
    /** 整页背景渐变（顶部 → 底部），浅色 */
    val gradient: List<Color>,
    /** 整页背景渐变，深色模式下使用 */
    val gradientDark: List<Color>,
    /** 主题色（浅色模式） */
    val accent: Color,
    /** 主题色（深色模式）：同色相提亮一档 */
    val accentDark: Color,
    /** 上半屏画什么 */
    val scene: SceneKind,
    /** 是否属于夜间主题（夜间自动切换只会选这一类） */
    val night: Boolean = false,
    /**
     * 夜间主题强制使用深色内容色：底色本身就是深空黑，
     * 沿用「跟随系统」的浅色内容会出现黑底黑字。
     */
    val forcesDarkContent: Boolean = night,
    /** 场景用色：主色 / 次色 / 高光 / 剪影 */
    val sceneA: Color = Color.Transparent,
    val sceneB: Color = Color.Transparent,
    val sceneC: Color = Color.Transparent,
    val sceneInk: Color = Color.Transparent,
    /**
     * 顶部雾化遮罩的强度倍率。
     * 场景画面本身已经很淡，这里只需再压一点点，保证小字（更新于 / 车名）稳得住。
     */
    val veilAlphaScale: Float = 0.16f,
    /** 底部渐隐（控件区）强度倍率。 */
    val scrimAlphaScale: Float = 1f,
) {
    fun gradient(dark: Boolean): List<Color> = if (dark) gradientDark else gradient

    fun accent(dark: Boolean): Color = if (dark) accentDark else accent

    fun palette(): ScenePalette = ScenePalette(a = sceneA, b = sceneB, c = sceneC, ink = sceneInk)

    val animated: Boolean get() = scene.animated
}

object SeasonThemes {

    // ========================================================================
    // 日间 · 静态 10 套
    // ========================================================================

    /** 晨雾青山：淡青底 + 三层山脊 + 山间雾 */
    val MIST_HILLS = SeasonTheme(
        id = "mist",
        label = "晨雾青山",
        mood = "淡青 · 山雾",
        gradient = listOf(Color(0xFFE4EFE9), Color(0xFFF0F7F3), Color(0xFFFAFCFA)),
        gradientDark = listOf(Color(0xFF11251A), Color(0xFF0D1410), Color(0xFF0B0E0C)),
        accent = Color(0xFF2F8F6B),
        accentDark = Color(0xFF4FBE93),
        scene = SceneKind.MIST_HILLS,
        sceneA = Color(0xFF7FA894),
        sceneB = Color(0xFFA9C4B3),
        sceneC = Color(0xFFFFE9B8),
        sceneInk = Color(0xFF4E6E60),
    )

    /** 晴空云絮：浅蓝底 + 云团 */
    val CLEAR_SKY = SeasonTheme(
        id = "sky",
        label = "晴空云絮",
        mood = "浅蓝 · 云团",
        gradient = listOf(Color(0xFFD6E9FA), Color(0xFFE9F3FD), Color(0xFFFAFCFF)),
        gradientDark = listOf(Color(0xFF0E2030), Color(0xFF0C141C), Color(0xFF0B0E11)),
        accent = Color(0xFF2C7BE5),
        accentDark = Color(0xFF5AA0F5),
        scene = SceneKind.CLEAR_SKY,
        sceneA = Color(0xFF9CC4E8),
        sceneB = Color(0xFFC7DDF2),
        sceneC = Color(0xFFFFF3C4),
        sceneInk = Color(0xFF7A93AB),
    )

    /** 春樱漫野：粉底 + 垂枝花簇 */
    val SAKURA = SeasonTheme(
        id = "sakura",
        label = "春樱漫野",
        mood = "暖粉 · 花枝",
        gradient = listOf(Color(0xFFFBE4EA), Color(0xFFFDF0F4), Color(0xFFFFF9FB)),
        gradientDark = listOf(Color(0xFF2A1620), Color(0xFF170E13), Color(0xFF0D080A)),
        accent = Color(0xFFE0658A),
        accentDark = Color(0xFFF58AA8),
        scene = SceneKind.BLOSSOM,
        sceneA = Color(0xFFF3B9C9),
        sceneB = Color(0xFFF8D5DF),
        sceneC = Color(0xFFFFB3C8),
        sceneInk = Color(0xFF8A5E6C),
    )

    /** 秋色森林：暖橙底 + 层林 */
    val AUTUMN = SeasonTheme(
        id = "autumn",
        label = "秋色森林",
        mood = "暖橙 · 层林",
        gradient = listOf(Color(0xFFFAE7CF), Color(0xFFFCF2E4), Color(0xFFFDF9F3)),
        gradientDark = listOf(Color(0xFF2A1E11), Color(0xFF16110C), Color(0xFF0D0B09)),
        accent = Color(0xFFCE6E28),
        accentDark = Color(0xFFF0924A),
        scene = SceneKind.AUTUMN_WOODS,
        sceneA = Color(0xFFE8B071),
        sceneB = Color(0xFFD98F4C),
        sceneC = Color(0xFFFFD9A0),
        sceneInk = Color(0xFF8A5A31),
    )

    /** 雪霁初晴：冷白底 + 雪坡反光 */
    val WINTER = SeasonTheme(
        id = "winter",
        label = "雪霁初晴",
        mood = "冷白 · 雪坡",
        gradient = listOf(Color(0xFFE3E8F0), Color(0xFFEFF2F7), Color(0xFFFAFBFD)),
        gradientDark = listOf(Color(0xFF1A222E), Color(0xFF12161D), Color(0xFF0C0E11)),
        accent = Color(0xFF52779F),
        accentDark = Color(0xFF8AAFCB),
        scene = SceneKind.SNOW_FIELD,
        sceneA = Color(0xFFC6D3E2),
        sceneB = Color(0xFFDCE5EF),
        sceneC = Color(0xFFFFF6D8),
        sceneInk = Color(0xFF8798AB),
    )

    /** 海岸微光：青白底 + 海平线碎光 */
    val SEASIDE = SeasonTheme(
        id = "seaside",
        label = "海岸微光",
        mood = "青白 · 海面",
        gradient = listOf(Color(0xFFDCF0F5), Color(0xFFEAF6FA), Color(0xFFF9FDFE)),
        gradientDark = listOf(Color(0xFF0B1F26), Color(0xFF071318), Color(0xFF04090B)),
        accent = Color(0xFF1F8FA8),
        accentDark = Color(0xFF4FBBD0),
        scene = SceneKind.SEASIDE,
        sceneA = Color(0xFF8CCBDA),
        sceneB = Color(0xFFB9E1EA),
        sceneC = Color(0xFFFFE7B0),
        sceneInk = Color(0xFF5E8B97),
    )

    /** 麦浪金黄：暖黄底 + 三层麦田 */
    val WHEAT_FIELD = SeasonTheme(
        id = "wheat",
        label = "麦浪金黄",
        mood = "暖黄 · 麦田",
        gradient = listOf(Color(0xFFFBEBCB), Color(0xFFFCF3E1), Color(0xFFFEFBF5)),
        gradientDark = listOf(Color(0xFF2A2011), Color(0xFF17120B), Color(0xFF0C0A06)),
        accent = Color(0xFFC08A2E),
        accentDark = Color(0xFFE5AE55),
        scene = SceneKind.WHEAT_FIELD,
        sceneA = Color(0xFFE7C179),
        sceneB = Color(0xFFD9A64F),
        sceneC = Color(0xFFFFD98C),
        sceneInk = Color(0xFF9A7333),
    )

    /** 湖光倒影：淡青底 + 平静湖面与倒影 */
    val LAKE_MIRROR = SeasonTheme(
        id = "lake",
        label = "湖光倒影",
        mood = "淡青 · 湖面",
        gradient = listOf(Color(0xFFDFEEF3), Color(0xFFECF5F8), Color(0xFFFAFCFD)),
        gradientDark = listOf(Color(0xFF0C1E24), Color(0xFF081318), Color(0xFF05090B)),
        accent = Color(0xFF2E8FA6),
        accentDark = Color(0xFF52B9D0),
        scene = SceneKind.LAKE_MIRROR,
        sceneA = Color(0xFFA9C9D4),
        sceneB = Color(0xFFC6E0E8),
        sceneC = Color(0xFFFFF2CE),
        sceneInk = Color(0xFF6E93A0),
    )

    /** 竹影清风：竹绿底 + 一排竹竿与竹叶 */
    val BAMBOO = SeasonTheme(
        id = "bamboo",
        label = "竹影清风",
        mood = "竹绿 · 疏影",
        gradient = listOf(Color(0xFFE4F0DE), Color(0xFFF0F8EC), Color(0xFFFAFDF8)),
        gradientDark = listOf(Color(0xFF13241A), Color(0xFF0D1710), Color(0xFF080D0A)),
        accent = Color(0xFF4A8B4E),
        accentDark = Color(0xFF6FBE72),
        scene = SceneKind.BAMBOO_GROVE,
        sceneA = Color(0xFF92B98A),
        sceneB = Color(0xFFB4D3AC),
        sceneC = Color(0xFFF2FBDA),
        sceneInk = Color(0xFF4C6E4A),
    )

    /** 沙丘暖阳：暖沙底 + 四层沙丘 */
    val DESERT = SeasonTheme(
        id = "dune",
        label = "沙丘暖阳",
        mood = "暖沙 · 丘脊",
        gradient = listOf(Color(0xFFFBE3D2), Color(0xFFFCEDE2), Color(0xFFFDF8F2)),
        gradientDark = listOf(Color(0xFF2B1D12), Color(0xFF18110B), Color(0xFF0D0906)),
        accent = Color(0xFFC97A3C),
        accentDark = Color(0xFFEAA05F),
        scene = SceneKind.DESERT_DUNE,
        sceneA = Color(0xFFE8B183),
        sceneB = Color(0xFFF0C9A5),
        sceneC = Color(0xFFFFDCA8),
        sceneInk = Color(0xFFA16C42),
    )

    // ========================================================================
    // 日间 · 动态 10 套
    // ========================================================================

    /** 流云漫步：云团横向缓慢漂移 */
    val CLOUD_DRIFT = SeasonTheme(
        id = "cloudflow",
        label = "流云漫步",
        mood = "动态 · 云漂移",
        gradient = listOf(Color(0xFFDFEEFA), Color(0xFFEDF5FD), Color(0xFFFBFDFF)),
        gradientDark = listOf(Color(0xFF0D1B2A), Color(0xFF0A121B), Color(0xFF080C10)),
        accent = Color(0xFF3D7FD6),
        accentDark = Color(0xFF63A2EE),
        scene = SceneKind.CLOUD_DRIFT,
        sceneA = Color(0xFFB7D3EA),
        sceneB = Color(0xFFD6E7F6),
        sceneC = Color(0xFFFFF0C9),
        sceneInk = Color(0xFF8AA2B8),
    )

    /** 落英缤纷：花瓣边落边摇 */
    val PETAL_FALL = SeasonTheme(
        id = "petalfall",
        label = "落英缤纷",
        mood = "动态 · 花瓣",
        gradient = listOf(Color(0xFFFCE9EF), Color(0xFFFDF2F6), Color(0xFFFFFAFC)),
        gradientDark = listOf(Color(0xFF2A1520), Color(0xFF170C12), Color(0xFF0C070A)),
        accent = Color(0xFFD9608A),
        accentDark = Color(0xFFF08CB0),
        scene = SceneKind.PETAL_FALL,
        sceneA = Color(0xFFF5C4D3),
        sceneB = Color(0xFFFADCE5),
        sceneC = Color(0xFFFFAEC6),
        sceneInk = Color(0xFF96687A),
    )

    /** 暖阳微尘：斜射光柱里浮尘缓慢上飘 */
    val SUN_DUST = SeasonTheme(
        id = "sundust",
        label = "暖阳微尘",
        mood = "动态 · 浮尘",
        gradient = listOf(Color(0xFFFBEFDC), Color(0xFFFDF6EA), Color(0xFFFFFCF6)),
        gradientDark = listOf(Color(0xFF2A2113), Color(0xFF17120B), Color(0xFF0C0906)),
        accent = Color(0xFFC4842F),
        accentDark = Color(0xFFE8A64F),
        scene = SceneKind.SUN_DUST,
        sceneA = Color(0xFFE7C89B),
        sceneB = Color(0xFFF3E2C7),
        sceneC = Color(0xFFFFD08A),
        sceneInk = Color(0xFF9C7B4E),
    )

    /** 细雨轻落：细雨斜落 + 远处山影 */
    val LIGHT_RAIN = SeasonTheme(
        id = "rain",
        label = "细雨轻落",
        mood = "动态 · 细雨",
        gradient = listOf(Color(0xFFE1E9EF), Color(0xFFEDF2F6), Color(0xFFF9FBFC)),
        gradientDark = listOf(Color(0xFF16202A), Color(0xFF0E151C), Color(0xFF090D11)),
        accent = Color(0xFF4A7A93),
        accentDark = Color(0xFF6FA3BC),
        scene = SceneKind.LIGHT_RAIN,
        sceneA = Color(0xFFAEC3CF),
        sceneB = Color(0xFFC9D8E1),
        sceneC = Color(0xFFFFF4D2),
        sceneInk = Color(0xFF71879A),
    )

    /** 涟漪湖面：水面涟漪外扩 + 波光缓移 */
    val RIPPLING_LAKE = SeasonTheme(
        id = "ripple",
        label = "涟漪湖面",
        mood = "动态 · 涟漪",
        gradient = listOf(Color(0xFFDCEEF6), Color(0xFFEAF4FA), Color(0xFFF9FCFE)),
        gradientDark = listOf(Color(0xFF0A1C26), Color(0xFF071219), Color(0xFF04080B)),
        accent = Color(0xFF2C89A8),
        accentDark = Color(0xFF4FB4D0),
        scene = SceneKind.RIPPLING_LAKE,
        sceneA = Color(0xFF9FC6D6),
        sceneB = Color(0xFFC2E0EA),
        sceneC = Color(0xFFFFF3D0),
        sceneInk = Color(0xFF6B8FA0),
    )

    /** 飞鸟掠空：云慢慢飘，一群雁横掠扇翅 */
    val BIRDS = SeasonTheme(
        id = "birds",
        label = "飞鸟掠空",
        mood = "动态 · 飞鸟",
        gradient = listOf(Color(0xFFDCEAFA), Color(0xFFEBF3FC), Color(0xFFFAFCFF)),
        gradientDark = listOf(Color(0xFF0C1A2A), Color(0xFF08111B), Color(0xFF05080D)),
        accent = Color(0xFF3F7FC0),
        accentDark = Color(0xFF66A6E0),
        scene = SceneKind.BIRDS_FLY,
        sceneA = Color(0xFFAEC9E4),
        sceneB = Color(0xFFCFDFF2),
        sceneC = Color(0xFFFFF0C6),
        sceneInk = Color(0xFF7E93AD),
    )

    /** 蒲公英舞：白色种子边升边摆 */
    val DANDELION = SeasonTheme(
        id = "dandelion",
        label = "蒲公英舞",
        mood = "动态 · 飞絮",
        gradient = listOf(Color(0xFFEDF5E6), Color(0xFFF5FAF0), Color(0xFFFCFEFA)),
        gradientDark = listOf(Color(0xFF16220F), Color(0xFF0E160A), Color(0xFF080C06)),
        accent = Color(0xFF74A34A),
        accentDark = Color(0xFF9CC96E),
        scene = SceneKind.DANDELION,
        sceneA = Color(0xFFC3D9AC),
        sceneB = Color(0xFFDEEBCE),
        sceneC = Color(0xFFFFFBDA),
        sceneInk = Color(0xFF83986B),
    )

    /** 晨雾流动：雾团沿山腰横向漂移 */
    val MIST_FLOW = SeasonTheme(
        id = "mistflow",
        label = "晨雾流动",
        mood = "动态 · 雾流",
        gradient = listOf(Color(0xFFE6EEF0), Color(0xFFF1F6F7), Color(0xFFFBFCFD)),
        gradientDark = listOf(Color(0xFF101B1E), Color(0xFF0A1214), Color(0xFF060A0B)),
        accent = Color(0xFF5C8A93),
        accentDark = Color(0xFF85B3BB),
        scene = SceneKind.MIST_FLOW,
        sceneA = Color(0xFFB7C9CD),
        sceneB = Color(0xFFD6E3E6),
        sceneC = Color(0xFFFFF6DC),
        sceneInk = Color(0xFF7C9296),
    )

    /** 树影斑驳：叶隙光斑在地面上呼吸、轻微晃动 */
    val TREE_SHADOW = SeasonTheme(
        id = "treeshadow",
        label = "树影斑驳",
        mood = "动态 · 光斑",
        gradient = listOf(Color(0xFFF4F0DE), Color(0xFFFAF7EC), Color(0xFFFEFDF8)),
        gradientDark = listOf(Color(0xFF221D10), Color(0xFF14110A), Color(0xFF0B0906)),
        accent = Color(0xFF9A8B3E),
        accentDark = Color(0xFFC0B25C),
        scene = SceneKind.TREE_SHADOW,
        sceneA = Color(0xFFCFC58F),
        sceneB = Color(0xFFE5DEBB),
        sceneC = Color(0xFFFFF3BE),
        sceneInk = Color(0xFF7A7343),
    )

    /** 云海日出：太阳在云海上呼吸 + 云海横向漂 */
    val SUNRISE = SeasonTheme(
        id = "sunrise",
        label = "云海日出",
        mood = "动态 · 日出",
        gradient = listOf(Color(0xFFFBE3D0), Color(0xFFFDF0E2), Color(0xFFFFF9F2)),
        gradientDark = listOf(Color(0xFF2A1812), Color(0xFF1A0E0A), Color(0xFF0C0605)),
        accent = Color(0xFFD2733A),
        accentDark = Color(0xFFF0985D),
        scene = SceneKind.SUNRISE_GLOW,
        sceneA = Color(0xFFE8A87E),
        sceneB = Color(0xFFF5C9A8),
        sceneC = Color(0xFFFFD98A),
        sceneInk = Color(0xFFA9704D),
    )

    // ========================================================================
    // 夜间 · 静态 10 套
    // ========================================================================

    /** 浩瀚星空：星点 + 细月 */
    val STARRY = SeasonTheme(
        id = "stars",
        label = "浩瀚星空",
        mood = "深空 · 星点",
        gradient = listOf(Color(0xFF0B1226), Color(0xFF070C18), Color(0xFF04060C)),
        gradientDark = listOf(Color(0xFF070C1A), Color(0xFF04070F), Color(0xFF020408)),
        accent = Color(0xFF6C8CFF),
        accentDark = Color(0xFF8AA4FF),
        scene = SceneKind.STARRY,
        night = true,
        sceneA = Color(0xFF2A3D6B),
        sceneB = Color(0xFF1A2547),
        sceneC = Color(0xFFBFD0FF),
        sceneInk = Color(0xFF05070E),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 城市天际：楼群剪影 + 窗灯 */
    val CITY_NIGHT = SeasonTheme(
        id = "city",
        label = "城市天际",
        mood = "暖灯 · 天际线",
        gradient = listOf(Color(0xFF171A2B), Color(0xFF0E1019), Color(0xFF07080C)),
        gradientDark = listOf(Color(0xFF11131F), Color(0xFF0A0B12), Color(0xFF050609)),
        accent = Color(0xFFC98A45),
        accentDark = Color(0xFFE8AC63),
        scene = SceneKind.CITY_NIGHT,
        night = true,
        sceneA = Color(0xFF2A2C46),
        sceneB = Color(0xFF1A1C2E),
        sceneC = Color(0xFFFFC977),
        sceneInk = Color(0xFF080A12),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 月色山谷：明月 + 山影 */
    val MOON_VALLEY = SeasonTheme(
        id = "moon",
        label = "月色山谷",
        mood = "冷月 · 山谷",
        gradient = listOf(Color(0xFF101A2E), Color(0xFF0A111F), Color(0xFF05080E)),
        gradientDark = listOf(Color(0xFF0C1424), Color(0xFF070C17), Color(0xFF04060A)),
        accent = Color(0xFF7E9BD0),
        accentDark = Color(0xFFA6BEEA),
        scene = SceneKind.MOON_VALLEY,
        night = true,
        sceneA = Color(0xFF233650),
        sceneB = Color(0xFF16243A),
        sceneC = Color(0xFFDCE6FF),
        sceneInk = Color(0xFF060A12),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 深蓝夜海：海面 + 月光倒影 */
    val NIGHT_SEA = SeasonTheme(
        id = "nightsea",
        label = "深蓝夜海",
        mood = "深蓝 · 海面",
        gradient = listOf(Color(0xFF0B1B2B), Color(0xFF071320), Color(0xFF040910)),
        gradientDark = listOf(Color(0xFF081520), Color(0xFF050E17), Color(0xFF03070B)),
        accent = Color(0xFF4F9BC4),
        accentDark = Color(0xFF77BBDD),
        scene = SceneKind.NIGHT_SEA,
        night = true,
        sceneA = Color(0xFF1A3A52),
        sceneB = Color(0xFF0E2437),
        sceneC = Color(0xFFCFE9FA),
        sceneInk = Color(0xFF03070C),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 银河星带：斜贯天幕的银河 */
    val MILKY_WAY = SeasonTheme(
        id = "milky",
        label = "银河星带",
        mood = "紫蓝 · 银河",
        gradient = listOf(Color(0xFF0A0F22), Color(0xFF060A16), Color(0xFF03050A)),
        gradientDark = listOf(Color(0xFF080B1A), Color(0xFF04060E), Color(0xFF020306)),
        accent = Color(0xFF8F7BE8),
        accentDark = Color(0xFFB0A0FF),
        scene = SceneKind.MILKY_WAY,
        night = true,
        sceneA = Color(0xFF2C2A5C),
        sceneB = Color(0xFF1B1B3E),
        sceneC = Color(0xFFE6DCFF),
        sceneInk = Color(0xFF04060D),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 山影层叠：四层山脊，越近越深 */
    val NIGHT_HILLS = SeasonTheme(
        id = "nighthills",
        label = "山影层叠",
        mood = "墨蓝 · 山脊",
        gradient = listOf(Color(0xFF121A24), Color(0xFF0B1017), Color(0xFF06080C)),
        gradientDark = listOf(Color(0xFF0E141C), Color(0xFF080B11), Color(0xFF040608)),
        accent = Color(0xFF5F7F97),
        accentDark = Color(0xFF8AAEC6),
        scene = SceneKind.NIGHT_HILLS,
        night = true,
        sceneA = Color(0xFF1E2A36),
        sceneB = Color(0xFF141C25),
        sceneC = Color(0xFF9FBED4),
        sceneInk = Color(0xFF05070A),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 松林夜雾：星空明月 + 三排塔形松 */
    val PINE_NIGHT = SeasonTheme(
        id = "pinenight",
        label = "松林夜雾",
        mood = "墨绿 · 松林",
        gradient = listOf(Color(0xFF0C1620), Color(0xFF070F16), Color(0xFF04080C)),
        gradientDark = listOf(Color(0xFF091219), Color(0xFF050C11), Color(0xFF020508)),
        accent = Color(0xFF5E8C7E),
        accentDark = Color(0xFF85B7A6),
        scene = SceneKind.PINE_NIGHT,
        night = true,
        sceneA = Color(0xFF2E4A46),
        sceneB = Color(0xFF203634),
        sceneC = Color(0xFFCFE8DC),
        sceneInk = Color(0xFF040A0B),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 雪夜村落：雪坡上几间亮着窗灯的房子 */
    val SNOW_VILLAGE = SeasonTheme(
        id = "village",
        label = "雪夜村落",
        mood = "暖灯 · 雪坡",
        gradient = listOf(Color(0xFF0E1726), Color(0xFF081020), Color(0xFF04070E)),
        gradientDark = listOf(Color(0xFF0A121D), Color(0xFF060C17), Color(0xFF030509)),
        accent = Color(0xFFC7975E),
        accentDark = Color(0xFFE8B87C),
        scene = SceneKind.SNOW_VILLAGE,
        night = true,
        sceneA = Color(0xFF3B4F6C),
        sceneB = Color(0xFF26344F),
        sceneC = Color(0xFFFFC978),
        sceneInk = Color(0xFF05080F),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 孤塔灯塔：海面岬角 + 灯塔与光束 */
    val LIGHTHOUSE = SeasonTheme(
        id = "lighthouse",
        label = "孤塔灯塔",
        mood = "暖光 · 灯塔",
        gradient = listOf(Color(0xFF0A1522), Color(0xFF06101A), Color(0xFF030810)),
        gradientDark = listOf(Color(0xFF08111B), Color(0xFF040C14), Color(0xFF02060A)),
        accent = Color(0xFFE0A35A),
        accentDark = Color(0xFFFFC77E),
        scene = SceneKind.LIGHTHOUSE,
        night = true,
        sceneA = Color(0xFF1D3145),
        sceneB = Color(0xFF12202F),
        sceneC = Color(0xFFFFD79A),
        sceneInk = Color(0xFF04070C),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 湖映星空：星空倒映在湖面上 */
    val LAKE_STARS = SeasonTheme(
        id = "lakestars",
        label = "湖映星空",
        mood = "静水 · 星影",
        gradient = listOf(Color(0xFF0A1224), Color(0xFF060C19), Color(0xFF03060C)),
        gradientDark = listOf(Color(0xFF070D1C), Color(0xFF040811), Color(0xFF020408)),
        accent = Color(0xFF6E8FE8),
        accentDark = Color(0xFF91AFFF),
        scene = SceneKind.LAKE_STARS,
        night = true,
        sceneA = Color(0xFF223258),
        sceneB = Color(0xFF131D36),
        sceneC = Color(0xFFDCE6FF),
        sceneInk = Color(0xFF03060B),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    // ========================================================================
    // 夜间 · 动态 10 套
    // ========================================================================

    /** 极光流淌：三道光带缓缓流动 */
    val AURORA = SeasonTheme(
        id = "aurora",
        label = "极光流淌",
        mood = "动态 · 极光",
        gradient = listOf(Color(0xFF071426), Color(0xFF050E1B), Color(0xFF02060C)),
        gradientDark = listOf(Color(0xFF05101F), Color(0xFF030A14), Color(0xFF010409)),
        accent = Color(0xFF3FCFA0),
        accentDark = Color(0xFF63E8BC),
        scene = SceneKind.AURORA,
        night = true,
        sceneA = Color(0xFF2E8FA8),
        sceneB = Color(0xFF6B5BC0),
        sceneC = Color(0xFF4CFFC4),
        sceneInk = Color(0xFF03080F),
        veilAlphaScale = 0.10f,
        scrimAlphaScale = 0.85f,
    )

    /** 流星划过：星星 + 间歇流星 */
    val METEOR = SeasonTheme(
        id = "meteor",
        label = "流星划过",
        mood = "动态 · 流星",
        gradient = listOf(Color(0xFF0A1024), Color(0xFF060915), Color(0xFF03050A)),
        gradientDark = listOf(Color(0xFF080C1C), Color(0xFF04060F), Color(0xFF020307)),
        accent = Color(0xFF7FA8FF),
        accentDark = Color(0xFFA3C2FF),
        scene = SceneKind.METEOR,
        night = true,
        sceneA = Color(0xFF22305A),
        sceneB = Color(0xFF141C36),
        sceneC = Color(0xFFE8F0FF),
        sceneInk = Color(0xFF04060C),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 萤火点点：光点浮游 + 明暗呼吸 */
    val FIREFLY = SeasonTheme(
        id = "firefly",
        label = "萤火点点",
        mood = "动态 · 萤火",
        gradient = listOf(Color(0xFF0D1A14), Color(0xFF08110D), Color(0xFF040806)),
        gradientDark = listOf(Color(0xFF0A1510), Color(0xFF060D09), Color(0xFF030604)),
        accent = Color(0xFF86D98A),
        accentDark = Color(0xFFA8ECA8),
        scene = SceneKind.FIREFLY,
        night = true,
        sceneA = Color(0xFF22402C),
        sceneB = Color(0xFF152A1C),
        sceneC = Color(0xFFD6FF9C),
        sceneInk = Color(0xFF040905),
        veilAlphaScale = 0.11f,
        scrimAlphaScale = 0.85f,
    )

    /** 夜雨阑珊：雨丝斜落 + 地面湿光 */
    val NIGHT_RAIN = SeasonTheme(
        id = "nightrain",
        label = "夜雨阑珊",
        mood = "动态 · 夜雨",
        gradient = listOf(Color(0xFF101A24), Color(0xFF0A1119), Color(0xFF05080C)),
        gradientDark = listOf(Color(0xFF0C141C), Color(0xFF070C12), Color(0xFF040608)),
        accent = Color(0xFF6E9BC0),
        accentDark = Color(0xFF93C0DE),
        scene = SceneKind.NIGHT_RAIN,
        night = true,
        sceneA = Color(0xFF243A4C),
        sceneB = Color(0xFF16242F),
        sceneC = Color(0xFFCFE4F5),
        sceneInk = Color(0xFF04070A),
        veilAlphaScale = 0.11f,
        scrimAlphaScale = 0.85f,
    )

    /** 星轨流转：同心弧缓慢旋转 */
    val STAR_TRAILS = SeasonTheme(
        id = "startrail",
        label = "星轨流转",
        mood = "动态 · 星轨",
        gradient = listOf(Color(0xFF0B1020), Color(0xFF060A16), Color(0xFF03050B)),
        gradientDark = listOf(Color(0xFF080C19), Color(0xFF04060F), Color(0xFF010307)),
        accent = Color(0xFF8C9BE8),
        accentDark = Color(0xFFAEBBFF),
        scene = SceneKind.STAR_TRAILS,
        night = true,
        sceneA = Color(0xFF2A3358),
        sceneB = Color(0xFF171D36),
        sceneC = Color(0xFFE6ECFF),
        sceneInk = Color(0xFF04060D),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 月影涟漪：月光在水面被打碎又聚拢 */
    val MOON_RIPPLE = SeasonTheme(
        id = "moonripple",
        label = "月影涟漪",
        mood = "动态 · 月影",
        gradient = listOf(Color(0xFF0B1826), Color(0xFF07111C), Color(0xFF040A10)),
        gradientDark = listOf(Color(0xFF08121C), Color(0xFF050D16), Color(0xFF02060A)),
        accent = Color(0xFF7FA8C8),
        accentDark = Color(0xFFA6CBE8),
        scene = SceneKind.MOON_RIPPLE,
        night = true,
        sceneA = Color(0xFF1F3448),
        sceneB = Color(0xFF122334),
        sceneC = Color(0xFFDCEBFA),
        sceneInk = Color(0xFF03080D),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 城市车流：楼群剪影 + 双向流动的车灯 */
    val NIGHT_TRAFFIC = SeasonTheme(
        id = "traffic",
        label = "城市车流",
        mood = "动态 · 车流",
        gradient = listOf(Color(0xFF151826), Color(0xFF0D1019), Color(0xFF07080D)),
        gradientDark = listOf(Color(0xFF101220), Color(0xFF0A0C14), Color(0xFF05060A)),
        accent = Color(0xFFE08A3C),
        accentDark = Color(0xFFFFA95C),
        scene = SceneKind.NIGHT_TRAFFIC,
        night = true,
        sceneA = Color(0xFF323759),
        sceneB = Color(0xFF20233A),
        sceneC = Color(0xFFFFC167),
        sceneInk = Color(0xFF06080E),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 夜雪纷飞：雪点下落 + 雪脊 */
    val SNOW_FALL = SeasonTheme(
        id = "snowfall",
        label = "夜雪纷飞",
        mood = "动态 · 落雪",
        gradient = listOf(Color(0xFF101A28), Color(0xFF0A121D), Color(0xFF050910)),
        gradientDark = listOf(Color(0xFF0C141F), Color(0xFF070D16), Color(0xFF03060A)),
        accent = Color(0xFF7FA6C9),
        accentDark = Color(0xFFA6C8E4),
        scene = SceneKind.SNOW_FALL,
        night = true,
        sceneA = Color(0xFF26384E),
        sceneB = Color(0xFF172537),
        sceneC = Color(0xFFFFFFFF),
        sceneInk = Color(0xFF040810),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 云遮明月：暗云缓缓掠过月亮 */
    val CLOUD_MOON = SeasonTheme(
        id = "cloudmoon",
        label = "云遮明月",
        mood = "动态 · 云度月",
        gradient = listOf(Color(0xFF0C1424), Color(0xFF081020), Color(0xFF04080F)),
        gradientDark = listOf(Color(0xFF091120), Color(0xFF050B18), Color(0xFF02050B)),
        accent = Color(0xFF9AA8D8),
        accentDark = Color(0xFFBCC8F2),
        scene = SceneKind.CLOUD_MOON,
        night = true,
        sceneA = Color(0xFF2C3D63),
        sceneB = Color(0xFF1F2C50),
        sceneC = Color(0xFFFFF2C7),
        sceneInk = Color(0xFF03060D),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    /** 潮汐夜浪：一排排浪线向岸边推进 */
    val NIGHT_WAVES = SeasonTheme(
        id = "nightwaves",
        label = "潮汐夜浪",
        mood = "动态 · 夜浪",
        gradient = listOf(Color(0xFF08131F), Color(0xFF050D16), Color(0xFF02060B)),
        gradientDark = listOf(Color(0xFF06101A), Color(0xFF040A11), Color(0xFF010408)),
        accent = Color(0xFF4E92B8),
        accentDark = Color(0xFF74BCDC),
        scene = SceneKind.NIGHT_WAVES,
        night = true,
        sceneA = Color(0xFF24425C),
        sceneB = Color(0xFF16303F),
        sceneC = Color(0xFFCFE9FA),
        sceneInk = Color(0xFF02060A),
        veilAlphaScale = 0.12f,
        scrimAlphaScale = 0.85f,
    )

    // ========================================================================

    /** 日间 20 套（10 静态 + 10 动态） */
    val DAY = listOf(
        MIST_HILLS, CLEAR_SKY, SAKURA, AUTUMN, WINTER, SEASIDE,
        WHEAT_FIELD, LAKE_MIRROR, BAMBOO, DESERT,
        CLOUD_DRIFT, PETAL_FALL, SUN_DUST, LIGHT_RAIN,
        RIPPLING_LAKE, BIRDS, DANDELION, MIST_FLOW, TREE_SHADOW, SUNRISE,
    )

    /** 夜间 20 套（10 静态 + 10 动态） */
    val NIGHT_ALL = listOf(
        STARRY, CITY_NIGHT, MOON_VALLEY, NIGHT_SEA, MILKY_WAY, NIGHT_HILLS,
        PINE_NIGHT, SNOW_VILLAGE, LIGHTHOUSE, LAKE_STARS,
        AURORA, METEOR, FIREFLY, NIGHT_RAIN,
        STAR_TRAILS, MOON_RIPPLE, NIGHT_TRAFFIC, SNOW_FALL, CLOUD_MOON, NIGHT_WAVES,
    )

    val ALL = DAY + NIGHT_ALL

    /**
     * 老版本存下来的主题 id。
     * 改名 / 删主题后旧偏好还要能落到最接近的一套上，不能让用户升级后主题突然被重置。
     *
     * 视频主题（四色四季 + 星空）已整体移除，这里把它们折回**画风最接近的实时绘制主题**：
     * 春日 → 春樱漫野、夏日 → 晴空云絮、秋日 → 秋色森林、冬日 → 雪霁初晴、星空 → 浩瀚星空。
     * `autumn / winter / stars` 的 id 一直没变，天然兼容。
     */
    private val LEGACY_IDS = mapOf(
        "spring" to SAKURA.id,
        "summer" to CLEAR_SKY.id,
        "video_spring" to SAKURA.id,
        "video_summer" to CLEAR_SKY.id,
        "video_autumn" to AUTUMN.id,
        "video_winter" to WINTER.id,
        "video_stars" to STARRY.id,
    )

    fun byId(id: String?): SeasonTheme {
        val key = LEGACY_IDS[id] ?: id
        return ALL.firstOrNull { it.id == key } ?: MIST_HILLS
    }

    fun isNightTheme(id: String?): Boolean = byId(id).night

    /** 深色槽为空时的默认主题（浩瀚星空）。 */
    val NIGHT = STARRY
}
