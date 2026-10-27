package com.leapcontrol.app.ui

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * 背景主题。
 *
 * 共 25 套：日间 10 套、夜间 10 套（各含 6 套静态 + 4 套动态），外加 5 套视频主题。
 *
 * 实时绘制的 20 套 = 整页基础渐变 + 上半屏的场景画面（[scene]，由 [paintScene] 实时绘制）+
 * 一个主题色（[accent]，作用于卡片底色、柱状图、强调元素）。
 * 另外 5 套（[VIDEO]）保留最初那批实拍视频背景，排在动态主题之后，`video = true`。
 *
 * 场景画面上限固定在上半屏，并做了两重衰减（场景自身淡出 + 顶部雾化），
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
    /**
     * 视频主题：播放打包在 res/raw 里的自带视频，不再绘制 [scene]。
     *
     * 这是最初那 5 套「四季 + 星空」的实拍视频背景，改版后作为独立分类保留在
     * 动态主题之后 —— 喜欢旧版的用户可以继续用，新用户默认走实时绘制的 20 套。
     */
    val video: Boolean = false,
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
    // 日间 · 静态 6 套
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

    // ========================================================================
    // 日间 · 动态 4 套
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

    // ========================================================================
    // 夜间 · 静态 6 套
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

    // ========================================================================
    // 夜间 · 动态 4 套
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

    // ========================================================================
    // 视频主题 5 套（旧版「四季 + 星空」实拍视频，改版后原样保留）
    // ========================================================================

    /** 春日花枝：视频主景在屏幕上部，需要较浓的雾把文字底下压干净 */
    val VIDEO_SPRING = SeasonTheme(
        id = "video_spring",
        label = "春日",
        mood = "视频 · 花枝",
        gradient = listOf(Color(0xFFD9F0E0), Color(0xFFEDF7EF), Color(0xFFF9FBF9)),
        gradientDark = listOf(Color(0xFF11251A), Color(0xFF0D1410), Color(0xFF0B0E0C)),
        accent = Color(0xFF2FA85C),
        accentDark = Color(0xFF46D27C),
        scene = SceneKind.NONE,
        video = true,
        veilAlphaScale = 1f,
    )

    /** 夏日晴空 */
    val VIDEO_SUMMER = SeasonTheme(
        id = "video_summer",
        label = "夏日",
        mood = "视频 · 晴空",
        gradient = listOf(Color(0xFFD3E9FB), Color(0xFFE7F3FC), Color(0xFFF8FBFE)),
        gradientDark = listOf(Color(0xFF0E2030), Color(0xFF0C141C), Color(0xFF0B0E11)),
        accent = Color(0xFF2C7BE5),
        accentDark = Color(0xFF5AA0F5),
        scene = SceneKind.NONE,
        video = true,
        veilAlphaScale = 1f,
    )

    /** 秋日暖叶 */
    val VIDEO_AUTUMN = SeasonTheme(
        id = "video_autumn",
        label = "秋日",
        mood = "视频 · 暖叶",
        gradient = listOf(Color(0xFFFAE7CF), Color(0xFFFCF2E4), Color(0xFFFDF9F3)),
        gradientDark = listOf(Color(0xFF2A1E11), Color(0xFF16110C), Color(0xFF0D0B09)),
        accent = Color(0xFFCE6E28),
        accentDark = Color(0xFFF0924A),
        scene = SceneKind.NONE,
        video = true,
        veilAlphaScale = 1f,
    )

    /** 冬日飘雪 */
    val VIDEO_WINTER = SeasonTheme(
        id = "video_winter",
        label = "冬日",
        mood = "视频 · 飘雪",
        gradient = listOf(Color(0xFFE3E8F0), Color(0xFFEFF2F7), Color(0xFFFAFBFD)),
        gradientDark = listOf(Color(0xFF1A222E), Color(0xFF12161D), Color(0xFF0C0E11)),
        accent = Color(0xFF52779F),
        accentDark = Color(0xFF8AAFCB),
        scene = SceneKind.NONE,
        video = true,
        veilAlphaScale = 1f,
    )

    /** 星空夜幕：视频本身够暗，雾只压住文字底下的星点，不能把星星糊掉 */
    val VIDEO_STARS = SeasonTheme(
        id = "video_stars",
        label = "星空",
        mood = "视频 · 夜空",
        gradient = listOf(Color(0xFF0B1226), Color(0xFF070C18), Color(0xFF04060C)),
        gradientDark = listOf(Color(0xFF070C1A), Color(0xFF04070F), Color(0xFF020408)),
        accent = Color(0xFF6C8CFF),
        accentDark = Color(0xFF8AA4FF),
        scene = SceneKind.NONE,
        video = true,
        night = true,
        veilAlphaScale = 0.35f,
        scrimAlphaScale = 0.85f,
    )

    // ========================================================================

    /** 日间 10 套（6 静态 + 4 动态） */
    val DAY = listOf(
        MIST_HILLS, CLEAR_SKY, SAKURA, AUTUMN, WINTER, SEASIDE,
        CLOUD_DRIFT, PETAL_FALL, SUN_DUST, LIGHT_RAIN,
    )

    /** 夜间 10 套（6 静态 + 4 动态） */
    val NIGHT_ALL = listOf(
        STARRY, CITY_NIGHT, MOON_VALLEY, NIGHT_SEA, MILKY_WAY, NIGHT_HILLS,
        AURORA, METEOR, FIREFLY, NIGHT_RAIN,
    )

    /** 视频主题 5 套（旧版四季 + 星空，排在动态主题之后） */
    val VIDEO = listOf(VIDEO_SPRING, VIDEO_SUMMER, VIDEO_AUTUMN, VIDEO_WINTER, VIDEO_STARS)

    val ALL = DAY + NIGHT_ALL + VIDEO

    /**
     * 老版本存下来的主题 id。
     * 改名后旧偏好还要能落到对应主题上，不能让用户升级后主题突然被重置。
     *
     * spring / summer 当初对应的是**视频**四季，所以直接落回视频版，
     * 升级后画面跟之前一模一样；autumn / winter / stars 的 id 没变，天然兼容。
     */
    private val LEGACY_IDS = mapOf(
        "spring" to VIDEO_SPRING.id,
        "summer" to VIDEO_SUMMER.id,
    )

    fun byId(id: String?): SeasonTheme {
        val key = LEGACY_IDS[id] ?: id
        return ALL.firstOrNull { it.id == key } ?: MIST_HILLS
    }

    fun isNightTheme(id: String?): Boolean = byId(id).night

    // ---------------- 夜间自动切换 ----------------

    /** 夜间自动切换的默认主题。 */
    val NIGHT = STARRY

    /** 夜间时段起点（含）：22:00。 */
    const val NIGHT_START_HOUR = 22

    /** 夜间时段终点（不含）：次日 06:00。 */
    const val NIGHT_END_HOUR = 6

    /** 当前本地时间是否落在夜间时段内（22:00 – 次日 06:00）。 */
    fun isNight(now: Calendar = Calendar.getInstance()): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }
}
