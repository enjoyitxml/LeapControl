package com.leapcontrol.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppearanceMode

/**
 * 「个性化」页（从「我的」进入，也可由首页顶部「皮肤」按钮直达）：
 *  - 外观模式：跟随系统 / 始终浅色 / 始终深色
 *  - 四季背景：春夏秋冬 + 星空（夜间）主题
 *  - 动态背景：背景视频开关 + 视频声音开关 + 夜间自动主题
 */
@Composable
fun PersonalizationScreen(
    runtime: AppRuntime,
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
    seasonId: String,
    onSeasonChange: (String) -> Unit,
    activeSeasonId: String,
    autoNightActive: Boolean,
    videoEnabled: Boolean,
    onVideoEnabledChange: (Boolean) -> Unit,
    videoSound: Boolean,
    onVideoSoundChange: (Boolean) -> Unit,
    nightAuto: Boolean,
    onNightAutoChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "个性化", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 外观模式 ----
        SettingsSection(title = "外观模式", desc = "浅色 / 深色") {
            SettingsCard {
                AppearanceModeOptions(mode = mode, onModeChange = onModeChange)
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 四季背景（含星空夜间主题） ----
        SettingsSection(title = "季节背景", desc = "主题配色") {
            SettingsCard {
                SeasonThemeGrid(
                    runtime = runtime,
                    seasonId = seasonId,
                    activeSeasonId = activeSeasonId,
                    autoNightActive = autoNightActive,
                    onSeasonChange = onSeasonChange,
                )
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    label = "夜间自动切换星空",
                    desc = "每天 ${SeasonThemes.NIGHT_START_HOUR}:00 – 次日 "
                        + "${SeasonThemes.NIGHT_END_HOUR}:00 自动切换到星空主题，"
                        + "白天恢复你选的季节（选择不会被覆盖）。星空为深色调，"
                        + "生效时界面文字会自动转浅色以保证可读",
                    checked = nightAuto,
                    onCheckedChange = onNightAutoChange,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 动态背景 ----
        SettingsSection(title = "动态背景", desc = "首页视频") {
            SettingsCard {
                SwitchRow(
                    label = "播放背景视频",
                    desc = "按当前季节循环播放动态背景（桃花 / 绿树 / 落叶 / 飘雪 / 星空）。关闭后回到纯色渐变，更省电",
                    checked = videoEnabled,
                    onCheckedChange = onVideoEnabledChange,
                )
                if (videoEnabled) {
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        label = "背景视频声音",
                        desc = "开启后播放自带的四季氛围配乐。默认关闭，避免打开 App 就出声",
                        checked = videoSound,
                        onCheckedChange = onVideoSoundChange,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * 「关于」页：版本信息 + 内嵌「免责声明」「隐私条款」两个子入口。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenDisclaimer: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "关于", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            InfoRow(label = "客户端", value = "零控 LeapControl")
            Spacer(Modifier.height(10.dp))
            InfoRow(label = "版本", value = appVersionText())
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                "零控是车主自发开发的非官方自用客户端，与零跑汽车官方无关。"
                    + "所有车况读取与控车指令均通过零跑官方云端接口下发，"
                    + "使用前请确保车辆处于安全状态，并遵守车辆使用规范。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "免责声明",
            desc = "软件性质、使用风险与责任免除条款",
            iconRes = com.leapcontrol.app.R.drawable.ic_disclaimer,
            onClick = onOpenDisclaimer,
        )
        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "隐私条款",
            desc = "数据全部存于本地，不收集任何隐私",
            iconRes = com.leapcontrol.app.R.drawable.ic_privacy,
            onClick = onOpenPrivacy,
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}
