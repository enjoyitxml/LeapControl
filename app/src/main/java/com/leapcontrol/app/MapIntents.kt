package com.leapcontrol.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/**
 * 去目的地的方式。
 *
 * 为什么需要这个枚举：「导航到车辆」以前一律走高德的驾车导航，可这个功能的真实场景是
 * **人已经在楼下、车停在几百米外的车位上** —— 开着车去找车这件事本身不成立。
 * 默认步行，才和「找车」这件事对得上；但也不能一刀切，停得远、下雨、带老人小孩时
 * 还是要能开车过去，所以让用户在弹层里自己选，步行排第一、标「推荐」。
 *
 * 两个参数是两套高德接口各自的口径，别混：
 *  - [amapT] = `androidamap://route/plan/` 的 `t`（0 驾车 / 2 步行 / 3 骑行）；
 *  - [webMode] = `uri.amap.com/navigation` 的 `mode`（car / walk / ride）。
 */
enum class NavMode(
    /** 弹层里的按钮文案。 */
    val label: String,
    /** 一句话说明，告诉用户什么情况下该选它。 */
    val hint: String,
    val amapT: Int,
    val webMode: String,
) {
    /** 找车的默认方式：车就在附近，走路过去最直接。 */
    WALK("步行", "就在附近时最直接", amapT = 2, webMode = "walk"),

    /** 停得稍远，或者懒得走。 */
    BIKE("骑行", "停放点离得稍远时", amapT = 3, webMode = "ride"),

    /** 停得很远 / 下雨 / 带了东西 —— 保留原有的驾车直达导航。 */
    CAR("驾车", "停得远或下雨时", amapT = 0, webMode = "car"),
}

/**
 * 地图调起封装。
 *
 * 坐标系说明：零跑车辆下发的是 GCJ-02，与高德一致，可直接传坐标。
 *
 * 「发送到车机」的实现路径：官方零跑 App 的车机导航同步依赖「手机端与车机端高德
 * 登录同一账号」，客户端侧没有公开的推送接口。因此这里调起高德的路径规划页
 * （androidamap://route/plan），高德在同账号下会把目的地同步到车机。
 */
object MapIntents {
    private const val APP_NAME = "零控"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun f(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun start(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 坐标型目的地（车辆位置）在导航里显示的名字。
     *
     * **绝不能再把逆地理出的详细地址当 `dname` 传** —— 高德会拿 `dname` 先去检索 POI，
     * 一旦命中就把我们传的 `dlat/dlon` 换成那个 POI 的坐标。用户报的就是这个：
     * 地址「…榆林路万科金域传奇」命中小区 POI，钉子被挪到小区中心，导航到的是小区不是车。
     * 传一个检索不到的通用名，坐标才保得住。
     */
    const val COORD_DEST_LABEL = "车辆位置"

    /**
     * 按指定方式导航到目的地。优先高德，回退系统地图 / 高德网页版。
     *
     * 步行和骑行走「路径规划页」而不是 `androidamap://navi`：navi 只做驾车导航，
     * 没有方式参数；路径规划页打开后是步行 / 骑行的路线，用户点开始即进入对应导航。
     *
     * 起点一律不填 —— 不申请定位权限，让高德用手机自己的定位（协议里写明 slat/slon 留空即用当前位置），
     * 这正是「起点 = 手机当前位置」想要的效果：用高德自己的实时定位，比我们读一次缓存坐标还准。
     *
     * [name] 只对「目的地本身就是一个 POI」的调用方有意义（搜索结果），
     * 坐标型目的地必须用默认值 [COORD_DEST_LABEL]，否则会被 POI 吸附。
     *
     * 返回 false 表示高德没起来（退到了系统地图或网页版，甚至完全打不开），
     * 调用方据此弹一句「没有找到可用的地图应用」。
     */
    fun navigate(
        context: Context,
        lat: Double,
        lng: Double,
        name: String = COORD_DEST_LABEL,
        mode: NavMode = NavMode.WALK,
    ): Boolean {
        if (start(context, Uri.parse(amapUri(lat, lng, name, mode)))) return true
        // 系统地图（只有驾车语义）只对驾车兜底：步行的时候跳进一个「驾车导航」更误导
        if (mode == NavMode.CAR && start(context, Uri.parse("google.navigation:q=${f(lat)},${f(lng)}"))) {
            return false
        }
        if (start(context, Uri.parse(webNavUri(lat, lng, name, mode)))) return false
        start(context, Uri.parse("geo:${f(lat)},${f(lng)}?q=${f(lat)},${f(lng)}(${enc(name)})"))
        return false
    }

    /**
     * 高德 App 的导航 URI。
     *
     * 返回字符串而不是 `Uri` 是为了能在本地单测里直接断言：`dname` 只要带出可检索的地址，
     * 高德就会用它检索 POI 并覆盖坐标 —— 这一点真机上一试一个准，必须在单测里提前拦住。
     */
    internal fun amapUri(lat: Double, lng: Double, name: String, mode: NavMode): String = when (mode) {
        // 驾车仍然直接进导航（这是原来唯一的行为，用户已习惯）
        NavMode.CAR -> "androidamap://navi?sourceApplication=$APP_NAME&poiname=${enc(name)}" +
            "&lat=${f(lat)}&lon=${f(lng)}&dev=0&style=2"
        // 步行 / 骑行：进路径规划页，t=2 步行、t=3 骑行
        else -> "androidamap://route/plan/?sourceApplication=$APP_NAME" +
            "&dlat=${f(lat)}&dlon=${f(lng)}&dname=${enc(name)}" +
            "&dev=0&t=${mode.amapT}&m=0"
    }

    /** 高德网页版导航 URI（没装 App 时的兜底）。注意 `to` 是「经度,纬度」。 */
    internal fun webNavUri(lat: Double, lng: Double, name: String, mode: NavMode): String =
        "https://uri.amap.com/navigation?to=${f(lng)},${f(lat)},${enc(name)}" +
            "&mode=${mode.webMode}&policy=1&src=mypage&coordinate=gaode&callnative=0"

    /**
     * 把目的地发到车机：调起高德路径规划。
     * 手机端与车机端高德登录同一账号时，上车后车机会提示是否继续导航。
     * 返回 true 表示成功唤起高德 App。
     */
    fun sendToCar(context: Context, lat: Double, lng: Double, name: String): Boolean {
        val amap = Uri.parse(
            "androidamap://route/plan/?dlat=${f(lat)}&dlon=${f(lng)}&dname=${enc(name)}&dev=0&t=0&sourceApplication=$APP_NAME"
        )
        if (start(context, amap)) return true
        val web = Uri.parse("https://uri.amap.com/navigation?to=${f(lng)},${f(lat)},${enc(name)}&mode=car&policy=1&src=mypage&coordinate=gaode&callnative=1")
        return start(context, web)
    }
}
