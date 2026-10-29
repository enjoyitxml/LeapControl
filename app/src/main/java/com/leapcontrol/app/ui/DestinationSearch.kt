package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.AmapPoi
import com.leapcontrol.app.R
import com.leapcontrol.app.MapIntents
import kotlinx.coroutines.launch

/**
 * 「发送到车机」——目的地搜索。
 *
 * 两件事拆开：
 *  - [DestinationSearchCard]：首页一张横条卡，点一下开弹层；
 *  - [DestinationSearchSheet]：搜索弹层，搜到 POI 后可以「发送到车机」或直接「导航」。
 *
 * 卡片默认位置紧跟空调条：它是出发前的高频动作，埋进「设置 → 高级设置」里等于不存在。
 * 唯一的前置条件是高德 Web Key，而弹层第一次打开就是申请教程 + 当场粘贴保存，
 * 配置不必再回设置页（那边「高德 Web Key」的入口留作备用）。
 *
 * 「发送到车机」的本质是**调起高德 App 的路径规划页**（`androidamap://route/plan`），
 * 手机与车机只要登录同一个高德账号，上车后车机就会提示继续导航 ——
 * 零跑官方没有对外的推送接口，这是唯一不需要抓协议的做法。
 */
@Composable
fun DestinationSearchCard(
    /** 高德 Web Key 是否可用：没有的话卡片照样能点（进去看教程），但副标题要说实话。 */
    hasKey: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与空调条同高，两张卡上下相邻时不至于一高一矮
            .heightIn(min = CARD_MIN_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(C.accent.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 定位图钉 + 仪表台轮廓的自绘图标（用户提供的图形）：
            // 比 Material 的实心箭头更像「把地点交给车」，也和首页其它卡片的图标语言一致
            Icon(
                painter = painterResource(R.drawable.ic_send_to_car),
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "将目的地发送到车机",
                color = C.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (hasKey) "搜个地址，手机与车机高德同账号即同步" else "还没配置高德 Key，点开看看怎么填",
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = C.textSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 目的地搜索弹层（屏幕正中的圆角卡片）。
 *
 * 用自绘 Dialog 而不是 `AlertDialog`：后者自带 Material 的按钮与输入框样式，
 * 在零控这套自绘卡片里会很突兀（输入框描边、按钮圆角、字号全对不上）。
 *
 * 弹层**不贴屏幕底边**：贴底时无论怎么算安全间距，都可能踩进系统手势区 / 小白条
 * （Dialog 拿不到导航栏 inset），过去两轮「按钮点不到」都是这个原因。居中之后四边留白，问题从根上消失。
 *
 * 返回键只收弹层（[BackHandler]），不会漏到首页的二级页返回逻辑上去。
 */
@Composable
fun DestinationSearchSheet(
    runtime: AppRuntime,
    onDismiss: () -> Unit,
    /** 下发结果提示（Toast）。 */
    onMessage: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AmapPoi>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    // 没 Key 时直接在这里填：看完教程就能粘进来搜，不用退出去翻设置
    var keyInput by remember { mutableStateOf("") }
    var keyJustSaved by remember { mutableStateOf(false) }

    val hasKey = runtime.hasAmapKey() || keyJustSaved

    fun reset() {
        results = emptyList()
        searched = false
    }

    fun search() {
        val text = keyword.trim()
        if (text.isEmpty()) {
            reset()
            return
        }
        // 搜之前先收起键盘：键盘占着下半屏时结果区只剩两三行，看着就像「页面被切掉了」
        focusManager.clearFocus()
        scope.launch {
            searching = true
            searched = true
            results = runCatching { runtime.searchDestination(text) }.getOrDefault(emptyList())
            searching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // 弹层开着时侧滑 / 返回键只收弹层，别把首页的二级页一起退了
        BackHandler(enabled = true, onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // —— 为什么是居中卡片而不是底部抽屉 ——
            // 底部抽屉不管怎么算 bottom gap，总会踩到系统手势区 / 小白条：Dialog 在
            // decorFitsSystemWindows = false 下拿不到导航栏 inset（`navigationBarsPadding()` 恒为 0，
            // 从 Activity 窗口读也不稳），而抽屉的「底边」正好是系统最敏感的那条线。
            // 改成屏幕正中的圆角卡片，四边都不贴边，这个界面里就再没有任何东西需要靠
            // 「离底边多少 dp」来保护 —— 键盘弹出时窗口被 adjustResize 压缩，卡片跟着在剩余空间里居中。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center,
            ) {
                // 上下各留出边距后的可用高度，卡片再高也不越过它
                val cardMaxHeight = (maxHeight - CARD_VERTICAL_MARGIN * 2).coerceAtLeast(220.dp)
                // 结果区：正常最多 296dp（约 5 条），屏幕被键盘压矮时按剩余空间收缩
                val resultMaxHeight = (cardMaxHeight - 168.dp)
                    .coerceIn(MIN_RESULT_AREA_HEIGHT, RESULT_AREA_HEIGHT)
                // 配置页是教程 + 输入框，比搜索页长得多，只在这个高度里滚
                val setupMaxHeight = (cardMaxHeight - 88.dp).coerceAtLeast(200.dp)

                Column(
                    modifier = Modifier
                        .padding(horizontal = CARD_HORIZONTAL_MARGIN)
                        .fillMaxWidth()
                        .widthIn(max = CARD_MAX_WIDTH)
                        // 自绘弹层内部必须吃东西，否则点空白处会穿透到遮罩上直接关掉
                        .consumeClicks()
                        .clip(RoundedCornerShape(22.dp))
                        .background(C.dialogBg)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "将目的地发送到车机",
                            color = C.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "关闭",
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    // 没配 Key 时整块换成「怎么申请 + 直接粘贴保存」，搜索框和结果区都不出现：
                    // 搜不出来的搜索框比一段教程更让人困惑
                    if (!hasKey) {
                        // 这一屏是教程 + 输入框 + 保存按钮，比搜索那一屏长得多；
                        // 点输入框时键盘再占掉半屏，不给滚动的话「保存 Key」就够不到了。
                        // 这一屏里没有列表，所以滚动不会和谁抢手势。
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = setupMaxHeight)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            KeySetup(
                                keyInput = keyInput,
                                onKeyChange = { keyInput = it },
                                onOpenConsole = { runtime.openExternal(AmapConsoleUrl) },
                                onSave = {
                                    val k = keyInput.trim()
                                    if (k.isNotEmpty()) {
                                        runtime.setAmapKey(k)
                                        keyJustSaved = true
                                        keyInput = ""
                                        onMessage("高德 Key 已保存，现在可以搜地址了")
                                    }
                                },
                            )
                        }
                    } else {
                        SearchField(
                            keyword = keyword,
                            onKeywordChange = {
                                keyword = it
                                reset()
                            },
                            onSearch = { search() },
                        )

                        Spacer(Modifier.height(12.dp))

                        // 结果区：一条结果就一行高，超过上限才在列表内部滚动。
                        // 操作直接做在每一行里：点一行 = 手机上导航，点右侧「发送」= 发到车机。
                        // 界面上因此**没有任何底部动作条** —— 那条正是反复被系统手势区盖住的地方。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = resultMaxHeight),
                        ) {
                            when {
                                searching -> HintBlock(title = "搜索中…", desc = null)

                                results.isEmpty() && searched -> HintBlock(
                                    title = "没有找到相关地点",
                                    desc = "换个说法试试，比如「西湖」而不是「西湖风景区停车场」。",
                                )

                                results.isEmpty() -> HintBlock(
                                    title = "搜一个要去的地方",
                                    desc = "搜到后点右侧「发送」，手机与车机登录同一个高德账号，" +
                                        "上车就会提示继续导航。",
                                )

                                // fillMaxWidth 而不是 fillMaxSize：要让列表按内容高度收缩，
                                // 卡片才会跟着结果条数变高变矮（fillMaxSize 会一直顶到上限）
                                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(results, key = { it.id.ifBlank { it.name + it.latitude } }) { poi ->
                                        PoiRow(
                                            poi = poi,
                                            onNavigate = {
                                                MapIntents.navigate(context, poi.latitude, poi.longitude, poi.name)
                                                onDismiss()
                                            },
                                            onSend = {
                                                val usedAmap = MapIntents.sendToCar(
                                                    context, poi.latitude, poi.longitude, poi.name,
                                                )
                                                onMessage(
                                                    if (usedAmap) {
                                                        "已在高德中规划「${poi.name}」，手机与车机同账号即可同步"
                                                    } else {
                                                        "没装高德地图，已用网页版打开「${poi.name}」"
                                                    },
                                                )
                                                onDismiss()
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // 没有底部动作条了，用一句话把两种操作说清楚（有结果时才出现）
                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点任意一条在手机地图里导航，点右侧「发送」发到车机",
                                color = C.textSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 没有 Key 时的整块配置区：为什么用不了 + 一键去申请 + 教程 + 当场粘贴保存。
 *
 * 顺序刻意是「先给结论，再给行动，最后才是细节」：
 * 用户点开卡片是想发地址，不是来读配置文档的，第一眼必须回答「我为什么卡在这儿、
 * 下一步该点哪里」。所以整宽的主按钮放在最上面，步骤和「申请之后能干嘛」往下排。
 *
 * 教程直接复用设置里那一份 `AmapKeyGuide`，两处文案永远一致，
 * 省得用户在卡片里看到一套步骤、进设置又看到另一套。
 */
@Composable
private fun KeySetup(
    keyInput: String,
    onKeyChange: (String) -> Unit,
    onOpenConsole: () -> Unit,
    onSave: () -> Unit,
) {
    val C = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部提示条：一句话说清「为什么用不了」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(C.accent.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send_to_car),
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "还不能用：没配高德 Web Key",
                    color = C.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "搜索地点、发送到车机都要用它，申请免费，三分钟搞定",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 主行动：整宽按钮直接跳高德开放平台（省得用户再去翻教程里那行网址）
        ActionButton(
            text = "去高德开放平台申请 Key",
            primary = true,
            enabled = true,
            onClick = onOpenConsole,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AmapKeyGuide(onOpenConsole = onOpenConsole)
        Spacer(Modifier.height(14.dp))
        Text(
            "申请到 32 位 Key 后，粘到下面保存",
            color = C.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        KeyField(value = keyInput, onValueChange = onKeyChange)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "也可以到「设置 → 发送到车机 → 高德 Web Key」里填",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                text = "保存 Key",
                primary = true,
                enabled = keyInput.isNotBlank(),
                onClick = onSave,
            )
        }
    }
}

/** Key 输入框：与搜索框同款圆角底 + 占位文案。 */
@Composable
private fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.chipBg)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "粘贴 32 位 Web 服务 Key",
                color = C.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = C.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(C.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 搜索框：自绘圆角底 + 放大镜 + 清空，样式与弹层其余部分同一套。 */
@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.chipBg)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 自绘圆润放大镜，不用 Material 的 Search：那个是实心细柄 + 直角，
        // 放在这套圆角卡片里显得又硬又小（用户反馈「换个更圆润好看的」）
        SearchGlyph(size = 18.dp, tint = C.textSecondary)
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (keyword.isEmpty()) {
                Text(
                    text = "输入地点名称，回车搜索",
                    color = C.textSecondary,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                textStyle = TextStyle(color = C.textPrimary, fontSize = 13.5.sp),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (keyword.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "清空",
                tint = C.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onKeywordChange("") },
            )
        }
    }
}

/**
 * 圆润放大镜。
 *
 * 不用 `Icons.Filled.Search`：那套图标是「实心细柄 + 直角收口」，在 17dp 这种小尺寸下
 * 看着又硬又碎，和零控这套大圆角、圆头描边的视觉语言不搭。
 * 这里自己画：一个正圆 + 一根圆头短柄，线宽与界面里的 1.5~2dp 描边同一档，
 * 放到任何主题色下都是同一个观感。
 */
@Composable
private fun SearchGlyph(size: Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.108f
        val r = s * 0.325f
        val c = Offset(s * 0.42f, s * 0.42f)
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = stroke))
        // 手柄：45° 圆头短柄。起笔取 0.92r —— 圆头正好压在圆环壁上，
        // 既不会在环内壁鼓出一个包（0.78r 时那样），也不会在接缝处露缝
        val d = r * 0.92f / 1.4142f
        drawLine(
            color = tint,
            start = Offset(c.x + d, c.y + d),
            end = Offset(s * 0.90f, s * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 结果区的一条 POI：名称 + 省市区地址，右侧一个「发送」胶囊。
 *
 * 整行点击 = 在手机地图里导航，「发送」= 发到车机。两个动作分开而不是先选中再点底部按钮：
 * 底部那条动作区在任何机型上都可能被系统手势区压住，做进行里就再没有这个问题，
 * 顺带少一次点击。
 */
@Composable
private fun PoiRow(
    poi: AmapPoi,
    onNavigate: () -> Unit,
    onSend: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onNavigate)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = poi.name,
                color = C.textPrimary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (poi.address.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = poi.address,
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // 主按钮不用 accent 实底 + 白字：主题色是用户自己选的，浅黄浅绿时白字会糊
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(C.accent.copy(alpha = 0.16f))
                .clickable(onClick = onSend)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = "发送",
                color = C.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 结果区的空态 / 错误态：标题一行 + 说明两行，垂直居中。 */
@Composable
private fun HintBlock(title: String, desc: String?) {
    val C = LocalAppColors.current
    Box(
        // 不 fillMaxSize：空态也要让弹层保持矮一点（搜索框 + 两行说明就够），
        // 否则一打开就是一整屏白底，看着像列表没加载出来
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HINT_BLOCK_MIN_HEIGHT)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (desc != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = desc,
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/**
 * 弹层底部按钮。
 *
 * 主按钮不用 `accent` 实底 + 白字：主题色是用户自己选的，选到浅黄 / 浅绿时白字会糊掉；
 * 统一走「淡色底 + 主题色字」，深浅主题都稳。
 */
@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val bg = when {
        !enabled -> C.chipBg.copy(alpha = 0.6f)
        primary -> C.accent.copy(alpha = 0.18f)
        else -> C.chipBg
    }
    val fg = when {
        !enabled -> C.textSecondary.copy(alpha = 0.55f)
        primary -> C.accent
        else -> C.textPrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.5.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 卡片最小高度：与首页空调条取同一数值，两块卡上下挨着时高度才对得上。 */
private val CARD_MIN_HEIGHT = 74.dp

/** 结果区上限定高：留够 4~5 条，再多就滚动，弹层不会随结果数量忽高忽低。 */
private val RESULT_AREA_HEIGHT = 296.dp

/** 空态 / 错误态的最小高度：够放「标题 + 两行说明」，卡片又不至于一打开就一大片白。 */
private val HINT_BLOCK_MIN_HEIGHT = 104.dp

/** 结果区的下限高度：屏幕被键盘压矮时，至少留一条半结果的高度给它。 */
private val MIN_RESULT_AREA_HEIGHT = 132.dp

/**
 * 卡片与屏幕上下边各留多少。
 *
 * 居中卡片的「安全边距」只用来保证不和状态栏 / 导航条视觉打架：
 * 卡片本身不贴边，所以系统手势区再高也压不到它。
 */
private val CARD_VERTICAL_MARGIN = 28.dp

/** 卡片左右留白：手机上比 Material 的 56dp 收紧一些，内容能多排两个字。 */
private val CARD_HORIZONTAL_MARGIN = 22.dp

/** 卡片最大宽度：平板上不跟着屏幕拉成一整行，保持对话框的观感。 */
private val CARD_MAX_WIDTH = 400.dp
