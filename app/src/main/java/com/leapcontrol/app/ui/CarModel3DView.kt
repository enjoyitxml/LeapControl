package com.leapcontrol.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.CarModel3DManager
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * 首页那块车模的 3D 版本。
 *
 * ## 它解决的是什么
 *
 * 2D 车模是一张服务端**预先渲染**的定妆照：接口只收 VIN，车型配色、轮毂、车顶
 * 全在服务端那一次渲染里定死。所以用户看到「颜色和配置跟我车不一样」时，改不了。
 * 3D 是拿官方下发的 `modelParam`（车型 / 年款 / 车漆色号 / 车顶色 / 座椅）在设备上
 * 实时渲染，这才是「跟官方一致」的做法。
 *
 * ## 拖动旋转
 *
 * 官方 H5 自带 OrbitControls，但它会吃掉所有触摸事件 —— 车模区域里页面就滑不动了。
 * 所以这里把 WebView 设成 `isEnabled = false`（只渲染、不收事件），触摸交给外层
 * Compose：
 *  - **横向拖动** → 注入 JS 绕车身转（见 [SPIN_JS]）；
 *  - **纵向拖动** → 完全不消费，交给首页的 `verticalScroll`。
 *
 * 用 [detectHorizontalDragGestures] 而不是自己判方向：它只在水平位移超过 touchSlop
 * 后才回调，纵向滑动一个事件都不消费，父级滚动的手感跟别处完全一样。
 *
 * ## 失败怎么办
 *
 * 25 秒还没首帧、或 H5 主动报初始化失败 → 回调 [onError]，首页换回 2D 图。
 * 宁可退回一张静态图，也不能在首屏留一块黑框。
 */
@Composable
fun CarModel3D(
    meta: CarModel3DMeta,
    /** 官方参数里没给车型时的兜底（取车辆档案的 carType）。 */
    fallbackCarType: String,
    /** 深浅色：只影响官方引擎的点灯与展台亮度。 */
    isDark: Boolean,
    /**
     * 页面正在滚动。
     *
     * 3D 是持续重绘的，滚动时还按 60 帧跑会和列表抢 GPU —— 降到 15 帧，
     * 车模看着还是静止的（它本来就不动），滚完再拉回来。
     *
     * 收 `State` 而不是 `Boolean`：滚动每变一次都传值的话，重组的是整个首页
     * （3000 多行的 Column），为了一个帧率数字不值当。这里只在自身作用域里读。
     */
    paused: State<Boolean>? = null,
    /** 点一下车模（和 2D 时一样：换主题壁纸）。null = 这个模式下不需要点击。 */
    onTap: (() -> Unit)? = null,
    /**
     * 车速（km/h）。
     *
     * 2D 时代这里叠的是自己画的抖动 + 风线；3D 用官方引擎自带的
     * `handleVehicleMove` —— 它会转车轮、让地面标线往后退，比抖一张图自然得多。
     */
    speedKmh: Float = 0f,
    /**
     * 场景还没画出来之前不要吃触摸。
     *
     * 否则 WebView 建场景那一两秒里，2D 图还显示着、点它换壁纸却点不动 ——
     * 用户只会觉得「这块坏了」。
     */
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var webView by remember(meta.h5Key) { mutableStateOf<CarModelWebView?>(null) }
    val pausedNow = paused?.value ?: false

    // 每次 meta 变了就重新来一个 WebView：换车型 / 换了资产包时旧的场景已经无效
    DisposableEffect(meta.h5Key, meta.srcKey) {
        onDispose {
            runCatching { webView?.destroy() }
            webView = null
        }
    }

    Box(
        modifier = modifier
            // 点一下换壁纸：和 2D 车模同一个行为，别因为换成 3D 就少一个入口
            .then(
                if (interactive && onTap != null) {
                    Modifier.pointerInput(onTap) { detectTapGestures { onTap() } }
                } else {
                    Modifier
                },
            )
            // 横向拖动才拦；纵向不消费，首页照常滚
            .then(
                if (interactive) {
                    Modifier.pointerInput(meta.h5Key) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            // 0.006 rad/px：横穿一屏（约 400px）转 137°，转半圈不用拖两次
                            webView?.spin(dragAmount * 0.006f)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CarModelWebView(
                    context = ctx,
                    modelDir = CarModel3DManager.modelDir(ctx, meta.h5Key),
                    modelParam = meta.modelParam,
                    fallbackCarType = fallbackCarType,
                    lastDark = isDark,
                    onReady = onReady,
                    onError = onError,
                ).also { webView = it }
            },
            update = { view ->
                view.setDark(isDark)
                view.setActive(!pausedNow)
                view.setSpeed(speedKmh)
                // factory 里已经 set 过一次；这里再兜一次是因为 factory 只跑一次，
                // 而回调是 Composable 每次重组都可能换新的 lambda
                view.onReadyCallback = onReady
                view.onErrorCallback = onError
            },
        )
    }
}

/** 给 Compose 侧用的元数据别名，避免 UI 层到处 import 内嵌类。 */
typealias CarModel3DMeta = CarModel3DManager.Meta

/**
 * 加载官方 3D 包并驱动它。
 *
 * 所有本地资源走 `https://appassets.androidplatform.net/...` 这个伪域名，
 * 由 [shouldInterceptRequest] 映射到磁盘文件 —— 这样 H5 里那些相对路径、
 * `fetch()`、`XMLHttpRequest` 全都能用，而且不会给 WebView 开 `file://` 权限。
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class CarModelWebView(
    context: Context,
    private val modelDir: File,
    private val modelParam: JSONObject?,
    private val fallbackCarType: String,
    private var lastDark: Boolean,
    onReady: () -> Unit,
    onError: () -> Unit,
) : WebView(context) {

    var onReadyCallback: () -> Unit = onReady
    var onErrorCallback: () -> Unit = onError

    private var sceneReady = false
    private var failed = false
    private var lastActive: Boolean? = null
    private var lastSpeed: Float? = null

    init {
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        // 不吃触摸：旋转由外层 Compose 注入，纵向留给首页滚动（见类注释）
        isEnabled = false

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? = serveLocal(request?.url)
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "3D JS: ${message?.message()} (${message?.lineNumber()})")
                }
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?,
            ): Boolean {
                result?.confirm()
                when (message) {
                    "onFirstFrame", "onFullCarLoaded" -> markReady(view)
                    "onInitThrow", "onInitTimeout" -> fail("初始化失败: $message")
                }
                return true
            }
        }

        loadUrl("https://$ASSETS_HOST/index.html")

        // 兜底：H5 里任何一步卡住（WebGL 创建失败、资产 404）都不会回调。
        // 25 秒还没首帧就当失败 —— 低端机解 12MB 资源确实要十几秒，判太紧会把
        // 「慢」误判成「坏」，这块就永远回不到 3D 了。
        postDelayed({
            if (!sceneReady) fail("25 秒内没有首帧")
        }, READY_TIMEOUT_MS)
    }

    // ------------------------------------------------------------ 对外操作

    /** 绕车身转 [deltaRadians]。正数 = 从车头往左看。 */
    fun spin(deltaRadians: Float) {
        if (deltaRadians == 0f || !sceneReady) return
        val value = String.format(Locale.US, "%.5f", deltaRadians)
        evaluateJavascript("window.__leapSpin && window.__leapSpin($value)", null)
    }

    fun setDark(dark: Boolean) {
        if (dark == lastDark) return
        lastDark = dark
        if (!sceneReady) return
        evaluateJavascript("window.__leapDark && window.__leapDark($dark)", null)
    }

    /** 滚动时降帧、停下来恢复。首次调用是 null → 会真的下发一次，正好把初始帧率定下来。 */
    fun setActive(active: Boolean) {
        if (lastActive == active) return
        lastActive = active
        if (!sceneReady) return
        evaluateJavascript("window.__leapFps && window.__leapFps(${if (active) 60 else 15})", null)
    }

    /** 车速：官方引擎转车轮 + 地面标线后退。停车时传 0，什么都不动。 */
    fun setSpeed(kmh: Float) {
        if (kmh == lastSpeed) return
        lastSpeed = kmh
        if (!sceneReady) return
        val value = String.format(Locale.US, "%.1f", kmh)
        evaluateJavascript("window.__leapSpeed && window.__leapSpeed($value)", null)
    }

    // ------------------------------------------------------------ 内部

    private fun markReady(view: WebView?) {
        if (sceneReady) return
        sceneReady = true
        onReadyCallback()
        view?.postDelayed({ tuneViewer(view) }, 120)
        view?.postDelayed({
            tuneViewer(view)
            applyParams(view)
        }, 420)
    }

    private fun fail(reason: String) {
        if (sceneReady || failed) return
        failed = true
        Log.w(TAG, "3D 车模不可用：$reason")
        onErrorCallback()
    }

    /**
     * 把伪域名映射到磁盘。
     *
     * 路径先做 canonical 校验再读：`..%2f` 这类 URL 编码穿越会被 normalize 掉，
     * 但多一层判断不亏 —— 这个目录里的内容全部来自服务端 zip。
     */
    private fun serveLocal(url: Uri?): WebResourceResponse? {
        if (url == null || url.host != ASSETS_HOST) return null
        val rawPath = url.path?.removePrefix("/") ?: return notFound()
        val decoded = runCatching { Uri.decode(rawPath) }.getOrDefault(rawPath)
        val root = modelDir.canonicalFile
        val file = File(root, decoded).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return notFound()

        val mime = when {
            decoded.endsWith(".html", true) -> "text/html"
            decoded.endsWith(".js", true) -> "application/javascript"
            decoded.endsWith(".json", true) -> "application/json"
            decoded.endsWith(".css", true) -> "text/css"
            decoded.endsWith(".png", true) -> "image/png"
            decoded.endsWith(".jpg", true) || decoded.endsWith(".jpeg", true) -> "image/jpeg"
            decoded.endsWith(".webp", true) -> "image/webp"
            decoded.endsWith(".gltf", true) -> "model/gltf+json"
            decoded.endsWith(".glb", true) -> "model/gltf-binary"
            decoded.endsWith(".fbx", true) -> "application/octet-stream"
            decoded.endsWith(".bin", true) -> "application/octet-stream"
            else -> "application/octet-stream"
        }
        return runCatching {
            // 二进制资源（贴图 / 模型 / 字体）不能带 charset：部分 WebView 会按文本解码，
            // 出来是花屏或者直接加载失败
            val isText = mime.startsWith("text/") ||
                mime == "application/javascript" ||
                mime == "application/json"
            WebResourceResponse(mime, if (isText) "UTF-8" else null, FileInputStream(file))
        }.getOrElse { notFound() }
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain", "UTF-8", 404, "Not Found",
        null, ByteArrayInputStream(ByteArray(0)),
    )

    /**
     * 官方 H5 的启动协议：等 `window.newInit` 出现，然后把渲染参数和视口尺寸递进去。
     *
     * `window.mobile` 那组回调是官方页面用来通知宿主机的（首帧、全车加载完、截图），
     * 我们只接首帧与错误 —— 截图给小组件那套这次不做。
     */
    private fun applyParams(view: WebView?) {
        val param = buildParamJson(modelParam, fallbackCarType, lastDark)
            .toString()
            .replace("'", "\\'")
        val script = """
        (function() {
            try {
                window.IOSWebview = false;
                window.AndroidWebview = true;
                window.mobile = window.mobile || {};
                window.mobile.onFirstFrame = function() { window.prompt('onFirstFrame'); };
                window.mobile.onFullCarLoaded = function() { window.prompt('onFullCarLoaded'); };
                window.mobile.printLog = function(l) { console.log(l); };
                var tries = 0;
                function start() {
                    if (typeof window.newInit === 'function') {
                        if (window.innerWidth > 0) {
                            var app = JSON.stringify({
                                width: window.innerWidth,
                                height: window.innerHeight,
                                energy: 0,
                                inland: 0
                            });
                            try {
                                window.newInit('$param', app);
                            } catch (e) {
                                window.prompt('onInitThrow', String(e && (e.message || e)));
                            }
                            return;
                        }
                        setTimeout(start, 100);
                        return;
                    }
                    if (++tries > 300) { window.prompt('onInitTimeout'); return; }
                    setTimeout(start, 50);
                }
                start();
            } catch (e) {
                window.prompt('onInitThrow', String(e));
            }
        })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    /**
     * 首帧之后的画面调优。
     *
     * 三件事，缺一不可：
     *  1. **透明**：关掉官方天空盒与 `scene.background`，否则车模带着一整套天空贴图，
     *     盖在我们的壁纸主题上像贴错图；
     *  2. **机位**：前侧 45° 俯视 + 放大到 1.12，车模体量才撑得住首页那 200dp；
     *  3. **锁视角动画**：官方加载完会自己补间一次正侧面，和上面定好的机位打架。
     */
    private fun tuneViewer(view: WebView?) {
        val speedLiteral = String.format(Locale.US, "%.1f", lastSpeed ?: 0f)
        val script = """
        (function() {
            var v = window.viewer;
            if (!v) return;
            var c = window.car || (v && v.car);

            if (v.frameState && v.frameState.skyBox) v.frameState.skyBox.visible = false;
            if (v.scene) v.scene.background = null;
            if (v.renderer) {
                v.renderer.setClearColor(0x000000, 0);
                v.renderer.setClearAlpha(0);
                var dpr = Math.min(Math.max(window.devicePixelRatio || 2.5, 2.5), 3.5);
                v.renderer.setPixelRatio(dpr);
                if (v.renderer.shadowMap) v.renderer.shadowMap.enabled = true;
            }
            if (document.body) document.body.style.background = 'transparent';
            if (document.documentElement) document.documentElement.style.background = 'transparent';

            var cam = (v.controls && v.controls.object) || v.camera;
            var ctrl = v.controls || v.control || v.orbitControls;
            if (c) c.__lockViewAnim__ = true;

            if (cam && ctrl && ctrl.target) {
                if (c && c.__viewAnimSet__ && typeof c.__viewAnimSet__.clear === 'function') {
                    c.__viewAnimSet__.clear();
                }
                ctrl.target.set(-0.35, 0.98, -0.35);
                cam.position.set(-11.15, 3.13, 10.45);
                cam.lookAt(ctrl.target);
                if (ctrl.target0) ctrl.target0.copy(ctrl.target);
                if (ctrl.position0) ctrl.position0.set(-11.15, 3.13, 10.45);
                if (typeof ctrl.saveState === 'function') ctrl.saveState();
                if (typeof ctrl.update === 'function') ctrl.update();
                if (c) { c.__oTheta__ = -0.785398; c.__oPhi__ = 1.32; }

                var zoom = 1.12;
                cam.zoom = zoom;
                if (!cam.__leapZoomHooked) {
                    cam.__leapZoomHooked = true;
                    var orig = cam.updateProjectionMatrix;
                    cam.updateProjectionMatrix = function() {
                        this.zoom = zoom;
                        orig.call(this);
                    };
                }
                cam.updateProjectionMatrix();
            }

            // 拖动旋转：绕 target 走球坐标。同时写 __oTheta__，
            // 因为官方引擎每帧会用这个值重算机位，只改相机位置会被它盖回去。
            window.__leapSpin = function(dRad) {
                var v2 = window.viewer; if (!v2) return;
                var c2 = window.car || v2.car;
                var ctrl2 = v2.controls || v2.control || v2.orbitControls;
                var cam2 = (ctrl2 && ctrl2.object) || v2.camera;
                if (c2) c2.__oTheta__ = ((typeof c2.__oTheta__ === 'number' ? c2.__oTheta__ : -0.785398) + dRad);
                if (!ctrl2 || !ctrl2.target || !cam2) return;
                var t = ctrl2.target;
                var ox = cam2.position.x - t.x, oy = cam2.position.y - t.y, oz = cam2.position.z - t.z;
                var r = Math.sqrt(ox*ox + oy*oy + oz*oz);
                if (!r) return;
                var theta = Math.atan2(ox, oz) + dRad;
                var phi = Math.acos(Math.max(-1, Math.min(1, oy / r)));
                cam2.position.x = t.x + r * Math.sin(phi) * Math.sin(theta);
                cam2.position.y = t.y + r * Math.cos(phi);
                cam2.position.z = t.z + r * Math.sin(phi) * Math.cos(theta);
                cam2.lookAt(t);
                if (typeof ctrl2.update === 'function') ctrl2.update();
                if (c2) c2.__lockViewAnim__ = true;
            };

            window.__leapDark = function(dark) {
                var v3 = window.viewer; if (!v3) return;
                var sky = v3.frameState && v3.frameState.skyBox;
                if (sky && typeof sky.setDayOrNight === 'function') sky.setDayOrNight(!!dark);
                var c3 = window.car || v3.car;
                if (c3 && typeof c3.handleLowBeamLight === 'function') c3.handleLowBeamLight(!!dark, true);
            };

            // 首页滚动时降到 15 帧：车模本身静止，帧率只影响拖动的跟手度，
            // 滚动那一两秒里它和列表抢 GPU 才是真的卡。
            window.__leapFps = function(fps) {
                var v4 = window.viewer; if (!v4) return;
                if (typeof v4.setFPS === 'function') v4.setFPS(fps);
                else if (v4.fpSetter && typeof v4.fpSetter.setFPS === 'function') v4.fpSetter.setFPS(fps);
            };

            // 行驶动效交给官方引擎：转车轮 + 地面标线后退。
            // 2D 时代这里是自己画的抖动和风线，3D 车模叠那个会露出背景边。
            window.__leapSpeed = function(kmh) {
                var v5 = window.viewer; if (!v5) return;
                var c5 = window.car || v5.car;
                if (!c5 || typeof c5.handleVehicleMove !== 'function') return;
                var n = Number(kmh) || 0;
                c5.handleVehicleMove(n);
                if (c5.__laneLine__) c5.__laneLine__.visible = n > 0;
            };
            window.__leapSpeed($speedLiteral);
            window.__leapDark($lastDark);
        })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private companion object {
        const val TAG = "CarModel3D"
        const val ASSETS_HOST = "appassets.androidplatform.net"
        const val READY_TIMEOUT_MS = 25_000L
    }
}

/**
 * 拼官方 `newInit()` 的渲染参数。
 *
 * 两个坑，都是踩出来的：
 *  - **年款必须 ≥ 2026**：官方引擎的动画参数表以 2026/2027 为基线，
 *    传 2025 会让 `this.params[year].door` 变成 undefined，整车动画初始化直接中断；
 *  - **不要自己映射字段**：`modelParam` 里服务端给什么就透传什么（车顶色、座椅、
 *    别名），少传一个就等于把那项配置打回默认值 —— 那正是「配置和官方不一样」的来源。
 */
internal fun buildParamJson(
    param: JSONObject?,
    fallbackCarType: String,
    isDark: Boolean,
): JSONObject {
    val obj = JSONObject()
    val carType = param?.optString("carType").orEmpty()
        .ifBlank { fallbackCarType }
        .ifBlank { "C16" }
    val rawYear = param?.optInt("year") ?: 2026
    val year = if (rawYear < 2026) 2026 else rawYear

    obj.put("carType", carType)
    obj.put("year", year)
    obj.put("carTypeCode", param?.optString("carTypeCode").orEmpty())
    obj.put("colorCode", param?.optInt("colorCode") ?: 0)
    obj.put("rudder", param?.optInt("rudder") ?: 0)
    // 固定机位：不让官方引擎自己算环绕半径与偏移
    obj.put("radius", 7.2)
    obj.put("offset", 0.0)
    obj.put("theme", "default")
    obj.put("isDark", isDark)

    param ?: return obj
    if (param.has("roofColorCode") && !param.isNull("roofColorCode")) {
        obj.put("roofColorCode", param.optInt("roofColorCode"))
    } else if (param.has("roofColor") && !param.isNull("roofColor")) {
        obj.put("roofColorCode", param.optInt("roofColor"))
    }
    if (param.has("seat") && !param.isNull("seat")) obj.put("seat", param.optInt("seat"))
    if (param.has("alias") && !param.isNull("alias")) obj.put("alias", param.optString("alias"))
    return obj
}
