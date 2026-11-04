package com.leapcontrol.app.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * 高德栅格瓦片源（路网 / 卫星）。
 *
 * ## 为什么用高德瓦片
 * 车端上报的坐标就是 GCJ-02（信号 2/3 那一对，见 [com.leapcontrol.app.VehicleLocationRules]），
 * 而高德瓦片本身就是 GCJ-02 切图 —— 把坐标原样交给地图控件，路网和车点天然对齐，
 * **不需要任何 WGS-84 换算**。换成 OSM 官方瓦片反而要纠偏，而且国内访问慢。
 *
 * ## 为什么不用 osmdroid 自带的 XYTileSource（踩过的坑）
 * `XYTileSource.getTileURLString()` 根本不认 `{x}/{y}/{z}` 占位符 —— 它是
 * `baseUrl + "/" + z + "/" + x + "/" + y + 扩展名`，把占位符原样留在 query 里。
 * 于是请求变成
 * `…appmaptile?...&x={x}&y={y}&z={z}/17/107000/53000.png`，高德直接回 **404 + HTML**，
 * 界面上就是一片空白格子（不是代码没跑，是每个瓦片都 404 了）。
 * 所以这里自己继承 [OnlineTileSourceBase]，把占位符替换掉（见 [tileUrl]）。
 *
 * ## 尺寸：高清和文字只能二选一（实测结论，别再试了）
 * 高清参数是 `scl=2`（不是 `scale=2`）：同一个瓦片号返回 512×512，覆盖范围与 256 版
 * 完全一致（缩到同尺寸逐通道平均差 1.3/255），确实是 2 倍分辨率 —— 但**整层文字注记被剥掉了**。
 * 实测 z=15/16/17/18、`webrd` 与 `wprd` 四个子域、`style=8/7`、加 `ltype`/`size`/`scale`/`scl`
 * 各种组合，512 图里都是 **0 个墨色像素**：路网和色块都在，路名、楼栋号一个没有。
 * 界面上就是「有路没字，根本不知道是哪儿」。
 *
 * 也试过「512 高清底图 + 256 透明注记层」两层叠：字回来了，但那份注记层
 * （`webst` + `style=8`，88% 透明）自带白色路面，按 3 倍放大叠上去后路缘变成一圈虚边，
 * 整张图的路像毛线团，比原来的「有点糊」更难看。没有「只含注记」的图层
 * （`style` 1~5/11 直接 404，9/10 是不透明的其它样式），所以这条路走不通。
 *
 * 结论：**256 的完整图（带字）就是这套接口的天花板**。真要街道级清晰，
 * 只能上高德地图 SDK（+12MB，且 Key 必须绑定包名 + 签名 SHA1，用户自己申请的 Key 用不了）。
 * 现在的清晰度靠 `MapView.setTilesScaledToDpi(true)` + 放大到 18 级来保证。
 *
 * ## 合规
 * 这是高德对外提供的公开瓦片服务地址，但不是官方文档里承诺「可给第三方 App 使用」的接口
 * —— 用它属于行业里常见的做法，没有 Key 也没有配额承诺。所以：
 *  - 只在我们自己这一页用，不做预取、不做批量抓取（osmdroid 默认就是按视口取当前需要的瓦片）；
 *  - 界面上保留「© 高德地图」署名，见 `FindCarMap`；
 *  - 真要上架应用商店的话，这一块需要换成有授权的瓦片服务。
 */
object AmapTileSource {

    /** 瓦片边长。高德这套地址给第三方用的栅格图最高就是 256。 */
    const val TILE_SIZE_PX = 256

    /** 四个子域轮询，避免单域名并发被限速。 */
    private val SUBDOMAINS = listOf("01", "02", "03", "04")

    /**
     * 路网图。`style=8` 是带 POI 标注的标准图（`style=7` 只有路网，没有地名）。
     *
     * **必须是 `scl=1`**：换成 `scl=2` 的 512 高清图会把整层文字注记剥掉
     * （实测所有层级、所有子域、所有 style 都没有一个墨色像素），界面上就成了
     * 「有路没字」，用户根本认不出是哪。详见类注释里的实测记录。
     *
     * 声明 3..18 级：z=19 高德大多只回纯色空图（见 [MapScale.MAX_ZOOM]）。
     */
    val road: OnlineTileSourceBase = TemplateTileSource(
        // 名字进了瓦片缓存的文件名。上一次缓存过一批 scl=2 的「无字」瓦片，
        // 换名字等于换缓存目录，否则旧脏数据会继续骑在上面（表现就是依然没字）
        name = "AmapRoadV4",
        minZoom = 3,
        maxZoom = 18,
        extension = ".png",
        templates = SUBDOMAINS.map { sub ->
            "https://webrd$sub.is.autonavi.com/appmaptile" +
                "?lang=zh_cn&size=1&scl=1&style=8&x={x}&y={y}&z={z}"
        }.toTypedArray(),
    )

    /**
     * 卫星影像。另一套域名（`webst`）+ `style=6`，返回的是 JPEG 而不是 PNG，
     * 所以后缀要给 `.jpg` —— 填错后缀 osmdroid 仍能解码，但缓存文件的扩展名会骗人。
     *
     * 影像**没有**高清版：`webst` 加 `scl=2` 实测仍回 256×256，所以卫星图在 3x 屏上
     * 还是 3 倍放大。先不为了"统一"去改它 —— 加了参数只会换来做不到的期待。
     *
     * 影像同样是 GCJ-02 加偏过的，和路网图共用同一套瓦片索引，切换时车点不会跳。
     * 深色主题下**不给卫星加滤镜**：影像本来偏暗，反相后像一张底片。
     */
    val satellite: OnlineTileSourceBase = TemplateTileSource(
        name = "AmapSatelliteV3",
        minZoom = 3,
        maxZoom = 18,
        extension = ".jpg",
        templates = SUBDOMAINS.map { sub ->
            "https://webst$sub.is.autonavi.com/appmaptile" +
                "?style=6&size=1&x={x}&y={y}&z={z}"
        }.toTypedArray(),
    )

    /**
     * 把 `{z}/{x}/{y}` 换成长整数值。
     *
     * 单独拎出来是为了能单测 —— 上一版的坑就是「以为库会替换、其实没替换」，
     * 而这种错在编译期完全看不出来，只能靠断言 URL。
     */
    internal fun tileUrl(template: String, z: Int, x: Int, y: Int): String = template
        .replace("{z}", z.toString())
        .replace("{x}", x.toString())
        .replace("{y}", y.toString())
}

/**
 * 认 `{x}/{y}/{z}` 占位符的在线瓦片源。
 *
 * [OnlineTileSourceBase] 自己不做任何拼接，只要求子类实现 [getTileURLString] ——
 * 正好够用，也不用把 osmdroid 的整套 TileSourceFactory 搬过来。
 */
private class TemplateTileSource(
    name: String,
    minZoom: Int,
    maxZoom: Int,
    extension: String,
    templates: Array<String>,
) : OnlineTileSourceBase(
    name,
    minZoom,
    maxZoom,
    AmapTileSource.TILE_SIZE_PX,
    extension,
    templates,
) {
    override fun getTileURLString(pMapTileIndex: Long): String = AmapTileSource.tileUrl(
        // 多子域时每次取一个是随机的，天然把并发摊到四个域名上
        getBaseUrl(),
        MapTileIndex.getZoom(pMapTileIndex),
        MapTileIndex.getX(pMapTileIndex),
        MapTileIndex.getY(pMapTileIndex),
    )
}
