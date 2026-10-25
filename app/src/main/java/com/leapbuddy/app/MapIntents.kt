package com.leapbuddy.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

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

    /** 以某坐标为中心做关键词周边搜索（如"充电站"）。优先高德，回退网页版。 */
    fun aroundSearch(context: Context, lat: Double, lng: Double, keyword: String): Boolean {
        val amap = Uri.parse(
            "androidamap://poilist?sourceApplication=$APP_NAME&keywords=${enc(keyword)}&lat=${f(lat)}&lon=${f(lng)}&dev=0"
        )
        if (start(context, amap)) return true
        val web = Uri.parse(
            "https://uri.amap.com/search?keyword=${enc(keyword)}&center=${f(lng)},${f(lat)}&src=$APP_NAME&callnative=0"
        )
        return start(context, web)
    }

    /** 在地图上显示一个点（车辆位置）。优先高德，回退系统地图。返回是否用了高德。 */
    fun showPoint(context: Context, lat: Double, lng: Double, label: String): Boolean {
        val amap = Uri.parse(
            "androidamap://viewMap?sourceApplication=$APP_NAME&poiname=${enc(label)}&lat=${f(lat)}&lon=${f(lng)}&dev=0"
        )
        if (start(context, amap)) return true
        val web = Uri.parse("https://uri.amap.com/marker?position=${f(lng)},${f(lat)}&name=${enc(label)}")
        if (start(context, web)) return false
        start(context, Uri.parse("geo:${f(lat)},${f(lng)}?q=${f(lat)},${f(lng)}(${enc(label)})"))
        return false
    }

    /** 直接导航到目的地。优先高德导航，回退系统地图。 */
    fun navigate(context: Context, lat: Double, lng: Double, name: String): Boolean {
        val amap = Uri.parse(
            "androidamap://navi?sourceApplication=$APP_NAME&poiname=${enc(name)}&lat=${f(lat)}&lon=${f(lng)}&dev=0&style=2"
        )
        if (start(context, amap)) return true
        if (start(context, Uri.parse("google.navigation:q=${f(lat)},${f(lng)}"))) return false
        val web = Uri.parse("https://uri.amap.com/navigation?to=${f(lng)},${f(lat)},${enc(name)}&mode=car&policy=1&src=mypage&coordinate=gaode&callnative=0")
        if (start(context, web)) return false
        start(context, Uri.parse("geo:${f(lat)},${f(lng)}?q=${f(lat)},${f(lng)}(${enc(name)})"))
        return false
    }

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
