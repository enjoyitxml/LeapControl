package com.leapcontrol.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.Commands
import com.leapcontrol.app.VehicleCapabilities

/**
 * 空调全屏二级页（点首页空调条进入）。
 *
 * ## 为什么从「向下展开」改成整页
 * 展开面板挤在空调条下面，宽度被卡片限死（约 320dp），一屏要放下温度、6 个场景、
 * 风量、出风方向、4 个座椅开关与 2 个座舱开关 —— 只能层层「展开更多设置」，
 * 结果最常用的座椅加热被藏在两级之后。整页之后可以按「高频在上、低频在下」平铺：
 * 温度 → 场景 → 风量 → 出风 → 舒适，一次滚动看到底，没有二级折叠。
 *
 * ## 与首页空调条的关系
 * 温度草稿由 HomeScreen 持有（[targetTemp]），两处加减号共用同一个值，
 * 不会出现「首页调了 24、进页面还是 22」。
 *
 * ## 为什么所有调整都要发「整组」acSetting
 * 车端没有「只调风量」这种单字段指令，每次调整都得把 operate / temperature /
 * windlevel / mode / circle / wshld / position 一起下发 —— 漏掉的字段车端会按默认值
 * 重置（比如调风量把温度带回 24℃）。所以这里所有调整都交给 [Commands.acSettingName]，
 * 把「当前值」原样带上，只改要改的那一项。
 *
 * ## 硬件门禁
 * 座椅加热 / 通风按座位判断，方向盘与后视镜加热单独判断，全部来自
 * [VehicleCapabilities]；一项都没有时整块「座椅与座舱舒适」不渲染。
 */
@Composable
fun AcScreen(
    status: CarStatus?,
    caps: VehicleCapabilities,
    busy: Boolean,
    targetTemp: Int,
    /** 正在下发的指令文案（如「主驾加热2档」），null 表示空闲。 */
    busyLabel: String?,
    /** 重新回拉车况：运行状态区右上角的「刷新」。 */
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    /** 点设定温度加减号（±1℃）。空调开着立即下发，关着只改草稿，见 [AcTempDraft]。 */
    onTempStep: (Int) -> Unit,
    /** 点电源：true = 开空调（带当前设定温度）。 */
    onPower: (Boolean) -> Unit,
    /** 其它一切指令（场景 / 风量 / 循环 / 出风方向 / 座椅…）都走这里，参数是命令名。 */
    onCommand: (String, rollback: (() -> Unit)?) -> Unit,
    onOpenScheduledAc: () -> Unit,
) {
    val C = LocalAppColors.current

    val acOn = status?.acOn == true
    val indoor = normalizeTemp(status?.interiorTemp)

    // 车端当前值：页面上所有「调一项、其余保持不变」的指令都拿它做底
    val remoteWind = status?.acWindLevel?.trim()?.toIntOrNull()
        ?.coerceIn(Commands.MIN_AC_WIND_LEVEL, Commands.MAX_AC_WIND_LEVEL)
        ?: DEFAULT_WIND
    val remotePosition = status?.acPosition ?: "all"
    val mode = status?.acMode ?: "nohotcold"
    val circle = if (status?.acCircle?.contains("外") == true) "out" else "in"

    // 风量与出风方向的本地值：点下去要立刻跟手，车端真值回来再覆盖。
    // remember(key) 让车端值一变就同步过来，点击期间车端值不变所以不会被打断。
    var wind by remember(remoteWind) { mutableStateOf(remoteWind) }
    var position by remember(remotePosition) { mutableStateOf(remotePosition) }

    // ---- 舒适件的本地档位 ----
    // 必须和风量一样「以本地为准、车端真值回来再覆盖」，原因有两个：
    // ① 座椅加热 / 通风 / 方向盘 / 后视镜这几个信号，车端回报常滞后甚至不上报，
    //    直接读 status 会一直显示 0 档 → 点第 2 下又发一次 1 档，永远关不掉；
    // ② 用户点完要立刻看到变化，不能等 3 秒回拉才亮。
    val remoteSeatHeatDriver = status?.seatHeating?.driver ?: 0
    val remoteSeatHeatPassenger = status?.seatHeating?.passenger ?: 0
    val remoteSeatVentDriver = status?.seatVentilation?.driver ?: 0
    val remoteSeatVentPassenger = status?.seatVentilation?.passenger ?: 0
    val remoteSteering = status?.steeringWheelHeating ?: 0
    val remoteMirror = if (status?.rearviewMirrorHeating == true) 1 else 0
    var seatHeatDriver by remember(remoteSeatHeatDriver) { mutableStateOf(remoteSeatHeatDriver) }
    var seatHeatPassenger by remember(remoteSeatHeatPassenger) { mutableStateOf(remoteSeatHeatPassenger) }
    var seatVentDriver by remember(remoteSeatVentDriver) { mutableStateOf(remoteSeatVentDriver) }
    var seatVentPassenger by remember(remoteSeatVentPassenger) { mutableStateOf(remoteSeatVentPassenger) }
    var steeringLevel by remember(remoteSteering) { mutableStateOf(remoteSteering) }
    var mirrorLevel by remember(remoteMirror) { mutableStateOf(remoteMirror) }

    // 空调开启时才挂无限旋转动画，关着的时候不空转
    val fanAngle = if (acOn) {
        val transition = rememberInfiniteTransition(label = "ac-screen-fan")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing)),
            label = "ac-screen-fan-angle",
        )
        angle
    } else {
        0f
    }

    /** 生成一条「只改一项」的空调指令名，其余字段沿用页面上的当前值。 */
    fun adjustName(
        op: String,
        windLevel: Int = wind,
        targetPosition: String = position,
        targetCircle: String = circle,
    ): String = Commands.acSettingName(
        op = op,
        temperature = targetTemp,
        windLevel = windLevel,
        mode = mode,
        circle = targetCircle,
        windshield = targetPosition == "wshld",
        position = targetPosition,
    )

    // 运行状态摘要：风量 / 循环 / 出风方向，一行说完，省掉三个独立状态行
    val summary = "$wind 挡风 · ${if (circle == "out") "外循环" else "内循环"} · " +
        if (position == "wshld") "前挡出风" else "全车出风"

    // ---- 运行状态清单 ----
    // 只用页面上「正在生效」的值（含刚点下去、还没等到车端回报的），
    // 这样点完座椅加热立刻能在列表里看到它，而不是等 3 秒回拉才发现「好像没开」。
    val runningItems = buildList {
        // 空调只写「运行中」不重复风量/循环：那几项在上面电源那一行已经写过了
        if (acOn) add("空调运行中")
        if (caps.seatHeating.driver && seatHeatDriver > 0) add("主驾座椅加热 $seatHeatDriver 档")
        if (caps.seatVentilation.driver && seatVentDriver > 0) add("主驾座椅通风 $seatVentDriver 档")
        if (caps.seatHeating.passenger && seatHeatPassenger > 0) add("副驾座椅加热 $seatHeatPassenger 档")
        if (caps.seatVentilation.passenger && seatVentPassenger > 0) add("副驾座椅通风 $seatVentPassenger 档")
        if (caps.steeringWheelHeating && steeringLevel > 0) {
            add("方向盘加热 ${if (steeringLevel >= 2) "强档" else "弱档"}")
        }
        if (caps.mirrorHeating && mirrorLevel > 0) add("后视镜加热")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 开了 enableEdgeToEdge 且 Scaffold inset 被清零，各页需自己避让系统栏
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        AcTopBar(onBack = onBack)

        // 正在下发：车控指令最长要等 24 秒才有回执，这段时间页面必须有反馈，
        // 否则点完座椅加热只有「按钮变灰」，用户根本不知道指令有没有发出去
        if (busyLabel != null) {
            Spacer(Modifier.height(8.dp))
            AcBusyStrip(label = busyLabel)
        }

        // ---- 温度主卡：设定温度 + 电源 ----
        AcSection(padding = 18.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TempStepButton(
                    plus = false,
                    enabled = !busy && AcTempDraft.canStep(targetTemp, -1),
                    onClick = { onTempStep(-1) },
                    touch = 44.dp,
                    arm = 15.dp,
                    label = "降低设定温度",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$targetTemp",
                            color = C.textPrimary,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-2).sp,
                            maxLines = 1,
                        )
                        Text(
                            // 用单字符 ℃ 而不是 "°C"：全角的 ° 会自带一格空白，
                            // 拆成两个字符时「24」与单位之间会空出一大块
                            text = "℃",
                            color = C.textPrimary.copy(alpha = 0.72f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            // 贴在数字肩部（上标手感）：数字行高约 70dp、单位行高约 26dp，
                            // 底部留 24dp 后它的顶端正好落在大字上三分之一处
                            modifier = Modifier.padding(bottom = 24.dp, start = 1.dp),
                        )
                    }
                    Text(
                        text = "设定温度",
                        color = C.textSecondary,
                        fontSize = 11.5.sp,
                    )
                }
                TempStepButton(
                    plus = true,
                    enabled = !busy && AcTempDraft.canStep(targetTemp, 1),
                    onClick = { onTempStep(1) },
                    touch = 44.dp,
                    arm = 15.dp,
                    label = "升高设定温度",
                )
            }

            Spacer(Modifier.height(16.dp))
            // 一条极淡的分隔线：把「调温区」与「开关区」分开，但不像分割线那样抢眼
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(C.textPrimary.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AcPowerButton(
                    acOn = acOn,
                    busy = busy,
                    angle = fanAngle,
                    size = 46.dp,
                    iconSize = 26.dp,
                    onClick = { onPower(!acOn) },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (acOn) "空调运行中" else "空调已关闭",
                        color = C.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (acOn) summary else "车内 $indoor℃",
                        color = C.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 电源的第二个入口：文字胶囊比圆钮更好读（圆钮上的风扇不像「开关」）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (acOn) C.textPrimary.copy(alpha = 0.06f)
                            else C.accent.copy(alpha = 0.14f),
                        )
                        .clickable(enabled = !busy) { onPower(!acOn) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (acOn) "关闭" else "开启",
                        color = when {
                            busy -> C.textSecondary.copy(alpha = 0.5f)
                            acOn -> C.textSecondary
                            else -> C.accent
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ---- 运行状态：一眼看清此刻到底有哪些功能在跑 ----
            // 没有这块时，用户点完座椅加热只能看档位点阵亮没亮，而档位信号车端常不上报，
            // 等于「开没开成功」全靠猜。
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(C.textPrimary.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "运行状态",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AcIcon(AcGlyph.Circulation, C.accent, size = 12.dp, stroke = 1.4.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "刷新",
                        color = C.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (runningItems.isEmpty()) {
                Text(
                    text = "当前没有开启的空调或舒适功能",
                    color = C.textSecondary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            } else {
                runningItems.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(C.accent),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(text = item, color = C.textPrimary, fontSize = 12.5.sp)
                    }
                }
            }
        }

        // ---- 快捷场景：4 格一行 ----
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AcSceneTile(
                glyph = AcGlyph.Snowflake,
                label = "极速降温",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("quickCool", null) },
            )
            AcSceneTile(
                glyph = AcGlyph.Sun,
                label = "一键制热",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("quickHeat", null) },
            )
            AcSceneTile(
                glyph = AcGlyph.Defrost,
                label = "前挡除霜",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("defrost", null) },
            )
            AcSceneTile(
                glyph = AcGlyph.Circulation,
                label = if (circle == "out") "内循环" else "外循环",
                modifier = Modifier.weight(1f),
                // 点亮表示「当前处于外循环」：与标签上的下一步动作相反，所以标题写的是下一步
                active = circle == "out",
                enabled = !busy,
                onClick = {
                    onCommand(
                        adjustName("circle", targetCircle = if (circle == "out") "in" else "out"),
                        null,
                    )
                },
            )
        }

        // ---- 除味 / 定时：两条宽行（比 4 格更适合「一次性动作」） ----
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AcWideTile(
                glyph = AcGlyph.Fresh,
                label = "快速除味",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("deodorize", null) },
            )
            AcWideTile(
                glyph = AcGlyph.Timer,
                label = "定时空调",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = onOpenScheduledAc,
            )
        }

        // ---- 风量：7 档平铺 ----
        // 不用滑杆：滑杆在 1..7 这种小数级上很难精确点到某一档（手指一动就跳两档），
        // 而空调风量恰恰是「离散、要准确」的参数。
        Spacer(Modifier.height(12.dp))
        AcSection(title = "风量") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (level in Commands.MIN_AC_WIND_LEVEL..Commands.MAX_AC_WIND_LEVEL) {
                    val selected = level == wind
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) C.accent else C.textPrimary.copy(alpha = 0.045f))
                            .clickable(enabled = !busy) {
                                if (level != wind) {
                                    // 先按当前值拼好指令，再改本地值：反过来的话
                                    // adjustName 会读到已经改过的 wind，把「旧值」当成新值发出去
                                    val command = adjustName("wind", windLevel = level)
                                    val previous = wind
                                    wind = level
                                    onCommand(command) { wind = previous }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$level",
                            color = when {
                                selected -> Color.White
                                !busy -> C.textSecondary
                                else -> C.textSecondary.copy(alpha = 0.45f)
                            },
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AcIcon(AcGlyph.Airflow, C.textSecondary, size = 15.dp, stroke = 1.4.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "当前 $wind 挡",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                )
            }
        }

        // ---- 出风方向 ----
        Spacer(Modifier.height(12.dp))
        AcSection(title = "出风方向") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AcChoiceTile(
                    glyph = AcGlyph.Wind,
                    label = "全车出风",
                    selected = position != "wshld",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (position != "all") {
                        val command = adjustName("position", targetPosition = "all")
                        val previous = position
                        position = "all"
                        onCommand(command) { position = previous }
                    }
                }
                AcChoiceTile(
                    glyph = AcGlyph.Defrost,
                    label = "前挡出风",
                    selected = position == "wshld",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (position != "wshld") {
                        val command = adjustName("position", targetPosition = "wshld")
                        val previous = position
                        position = "wshld"
                        onCommand(command) { position = previous }
                    }
                }
            }
        }

        // ---- 座椅与座舱舒适 ----
        val hasDriver = caps.seatHeating.driver || caps.seatVentilation.driver
        val hasPassenger = caps.seatHeating.passenger || caps.seatVentilation.passenger
        if (hasDriver || hasPassenger || caps.steeringWheelHeating || caps.mirrorHeating) {
            Spacer(Modifier.height(12.dp))
            AcSection(title = "座椅与座舱舒适") {
                if (hasDriver || hasPassenger) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasDriver) {
                            AcSeatBlock(
                                title = "主驾驶位",
                                heatingLevel = if (caps.seatHeating.driver) seatHeatDriver else null,
                                ventilationLevel = if (caps.seatVentilation.driver) seatVentDriver else null,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                onHeating = { lvl ->
                                    val previous = seatHeatDriver
                                    seatHeatDriver = lvl
                                    onCommand("driverSeatHeating_$lvl") { seatHeatDriver = previous }
                                },
                                onVentilation = { lvl ->
                                    val previous = seatVentDriver
                                    seatVentDriver = lvl
                                    onCommand("driverSeatVentilation_$lvl") { seatVentDriver = previous }
                                },
                            )
                        }
                        if (hasPassenger) {
                            AcSeatBlock(
                                title = "副驾驶位",
                                heatingLevel = if (caps.seatHeating.passenger) seatHeatPassenger else null,
                                ventilationLevel = if (caps.seatVentilation.passenger) seatVentPassenger else null,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                onHeating = { lvl ->
                                    val previous = seatHeatPassenger
                                    seatHeatPassenger = lvl
                                    onCommand("passengerSeatHeating_$lvl") { seatHeatPassenger = previous }
                                },
                                onVentilation = { lvl ->
                                    val previous = seatVentPassenger
                                    seatVentPassenger = lvl
                                    onCommand("passengerSeatVentilation_$lvl") { seatVentPassenger = previous }
                                },
                            )
                        }
                    }
                }

                if (caps.steeringWheelHeating || caps.mirrorHeating) {
                    if (hasDriver || hasPassenger) Spacer(Modifier.height(10.dp))
                    AcRowGroup {
                        if (caps.steeringWheelHeating) {
                            AcLevelRow(
                                glyph = AcGlyph.Steering,
                                label = "方向盘加热",
                                level = steeringLevel,
                                maxLevel = 2,
                                enabled = !busy,
                                // 0 关 → 1 弱 → 2 强 → 0 关
                                onClick = {
                                    val next = (steeringLevel + 1) % 3
                                    val previous = steeringLevel
                                    steeringLevel = next
                                    onCommand("steeringWheelHeating_$next") { steeringLevel = previous }
                                },
                            )
                        }
                        if (caps.steeringWheelHeating && caps.mirrorHeating) {
                            AcRowDivider()
                        }
                        if (caps.mirrorHeating) {
                            AcLevelRow(
                                glyph = AcGlyph.Mirror,
                                label = "后视镜加热",
                                level = mirrorLevel,
                                maxLevel = 1,
                                enabled = !busy,
                                onClick = {
                                    val next = if (mirrorLevel > 0) 0 else 1
                                    val previous = mirrorLevel
                                    mirrorLevel = next
                                    onCommand(
                                        if (next > 0) "rearviewMirrorHeating_on"
                                        else "rearviewMirrorHeating_off",
                                    ) { mirrorLevel = previous }
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** 没读到车端风量时的默认挡位（与 [Commands.AC_ON_STATE] 的 2 挡一致）。 */
private const val DEFAULT_WIND = 2

/**
 * 把命令名翻成一句「人要读的话」，用于二次确认弹窗与「正在下发」提示。
 *
 * 命令名的中文标签统一由 [Commands.build] 提供（`driverSeatHeating_2` → 「主驾加热2档」），
 * 这里只是兜住非法命令名的异常，不让一个文案把页面搞崩。
 */
internal fun acCommandLabel(commandName: String): String = when (commandName) {
    // 温度加减没有对应命令名（走 presetAcTemperature 参数），单独给一句
    "acTemp" -> "调整设定温度"
    else -> runCatching { Commands.build(commandName).label }.getOrElse { "空调操作" }
}

/** 「正在下发：xxx，等待车端确认…」提示条。 */
@Composable
private fun AcBusyStrip(label: String) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.accent.copy(alpha = 0.12f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.8.dp,
            color = C.accent,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "正在下发：$label，等待车端确认…",
            color = C.accent,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 页面骨架
// ---------------------------------------------------------------------------

/** 顶栏：左侧返回圆钮 + 居中标题。 */
@Composable
private fun AcTopBar(onBack: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
                .clip(CircleShape)
                // 返回钮直接压在季节渐变上（不在卡片里），淡透明底会看不见，
                // 所以用不透明 panelBg 做一颗白色圆钮
                .background(C.panelBg)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            AcIcon(AcGlyph.ChevronLeft, C.textPrimary, size = 17.dp, stroke = 1.9.dp)
        }
        Text(
            text = "空调",
            color = C.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * 一块实心卡片。整页由若干块拼成，不画阴影：首页本身是渐变背景，
 * 这里再叠阴影会让页面显脏（与「控车提示」那次踩的坑同源）。
 */
@Composable
private fun AcSection(
    title: String? = null,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.panelBg, RoundedCornerShape(18.dp))
            .padding(padding),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = C.textSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

// ---------------------------------------------------------------------------
// 格子 / 行
// ---------------------------------------------------------------------------

/**
 * 场景格：图标放在淡色圆底里、文字在下。
 *
 * 图标单独加一层圆底，是为了让 4 个格子的视觉重心一致 —— 直接放裸图标时，
 * 雪花（满幅）与定时（方框）的墨量差异很大，一排看过去会一重一轻。
 */
@Composable
private fun AcSceneTile(
    glyph: AcGlyph,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val content = when {
        !enabled -> C.textSecondary.copy(alpha = 0.42f)
        active -> C.accent
        else -> C.textPrimary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) C.accent.copy(alpha = 0.14f) else C.textPrimary.copy(alpha = 0.045f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                // 选中态用主题色淡圈，未选中用不透明卡片色做白圆盘 ——
                // 用半透明黑会把「淡底上的淡圆」糊成一片，白圆盘才有层次
                .background(if (active) C.accent.copy(alpha = 0.16f) else C.panelBg),
            contentAlignment = Alignment.Center,
        ) {
            AcIcon(glyph, content, size = 19.dp, stroke = 1.5.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = content,
            fontSize = 11.5.sp,
            maxLines = 1,
        )
    }
}

/** 宽行：图标 + 文字 + 右箭头，用于「除味 / 定时」这类一次性动作。 */
@Composable
private fun AcWideTile(
    glyph: AcGlyph,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.textPrimary.copy(alpha = 0.045f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AcIcon(
            glyph,
            if (enabled) C.textPrimary else C.textSecondary.copy(alpha = 0.42f),
            size = 19.dp,
            stroke = 1.5.dp,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = if (enabled) C.textPrimary else C.textSecondary.copy(alpha = 0.42f),
            fontSize = 12.5.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        AcIcon(AcGlyph.ChevronRight, C.textSecondary.copy(alpha = 0.6f), size = 14.dp, stroke = 1.7.dp)
    }
}

/** 出风方向的选择格：图标 + 文字横向排布，选中态淡主题色底 + 主题色文字。 */
@Composable
private fun AcChoiceTile(
    glyph: AcGlyph,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val content = when {
        !enabled -> C.textSecondary.copy(alpha = 0.42f)
        selected -> C.accent
        else -> C.textSecondary
    }
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) C.accent.copy(alpha = 0.14f) else C.textPrimary.copy(alpha = 0.045f), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AcIcon(glyph, content, size = 18.dp, stroke = 1.5.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = content,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 一个座位的两块开关（加热 / 通风）。
 *
 * 档位用三颗小圆点而不是「2 档」文字：点阵能一眼看出「现在是几档、最多几档」，
 * 文字要读完才知道，而且不同车型档位数不同（这里按 0-3 处理，超出部分车端会忽略）。
 */
@Composable
private fun AcSeatBlock(
    title: String,
    heatingLevel: Int?,
    ventilationLevel: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onHeating: (Int) -> Unit,
    onVentilation: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.textPrimary.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Text(text = title, color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        if (heatingLevel != null) {
            AcLevelRow(
                glyph = AcGlyph.SeatHeat,
                // 座位卡标题已经写了「主驾驶位 / 副驾驶位」，行内再写「座椅加热」会挤掉档位条
                label = "加热",
                level = heatingLevel,
                maxLevel = 3,
                enabled = enabled,
                compact = true,
                // 0 → 1 → 2 → 3 → 0
                onClick = { onHeating((heatingLevel + 1) % 4) },
            )
            if (ventilationLevel != null) Spacer(Modifier.height(6.dp))
        }
        if (ventilationLevel != null) {
            AcLevelRow(
                glyph = AcGlyph.SeatVent,
                label = "通风",
                level = ventilationLevel,
                maxLevel = 3,
                enabled = enabled,
                compact = true,
                onClick = { onVentilation((ventilationLevel + 1) % 4) },
            )
        }
    }
}

/** 把若干 [AcLevelRow] 收进一个淡底容器，行之间用 [AcRowDivider] 分隔。 */
@Composable
private fun AcRowGroup(content: @Composable ColumnScope.() -> Unit) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.textPrimary.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun AcRowDivider() {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 12.dp)
            .height(1.dp)
            .background(C.textPrimary.copy(alpha = 0.05f)),
    )
}

/**
 * 带档位的一行开关：图标 + 名称 + 「关 / 1 / 2 / 3」四段档位条。
 *
 * ## 为什么从「点阵」改成「分段」
 * 点阵只能看出「大概亮了几颗」，用户反馈「看不到具体档位」：
 * 3 颗全亮既可能是 3 档也可能是 2 档（第 3 颗没亮时更难分辨），
 * 而且想关掉只能一路点回 0 档。改成分段之后，当前档位有明确的高亮格，
 * 点哪一档就是哪一档、点「关」立刻归零 —— 开和关都是一步。
 *
 * @param onClick 点整行：按 0→1→…→max→0 循环（老习惯，肌肉记忆不用改）
 * @param onSelect 点某一段：直接跳到该档位
 * @param compact 嵌在座位卡里时用（行高更低、左右内边距更小）
 */
@Composable
private fun AcLevelRow(
    glyph: AcGlyph,
    label: String,
    level: Int,
    maxLevel: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val on = level > 0
    val content = when {
        !enabled -> C.textSecondary.copy(alpha = 0.42f)
        on -> C.accent
        else -> C.textPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 嵌在座位卡（本身已是 chipBg）里时不能再铺 chipBg，否则行与卡同色糊在一起，
            // 改成一层极淡的主色底来区分「可点的一行」
            .clip(RoundedCornerShape(10.dp))
            .background(if (compact) C.panelBg.copy(alpha = 0.55f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 7.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AcIcon(glyph, content, size = if (compact) 16.dp else 18.dp, stroke = 1.5.dp)
        Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
        Text(
            text = label,
            color = content,
            fontSize = if (compact) 11.5.sp else 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        // 档位点阵：亮几颗就是几档（1 档 1 颗绿点、2 档 2 颗…）。
        // 之前那版是「关 / 1 / 2 / 3」四个文字格，摆在座位卡里跟图标、文字挤成一条，
        // 几乎连在一起 —— 点阵只占三十几 dp，而且「亮了几颗」一眼就能数出来。
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 1..maxLevel) {
                val lit = i <= level
                Box(
                    modifier = Modifier
                        .size(if (compact) 6.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                lit -> content
                                else -> C.textPrimary.copy(alpha = if (enabled) 0.14f else 0.07f)
                            },
                        ),
                )
            }
        }
    }
}

