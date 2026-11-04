package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ConfirmAction
import com.leapcontrol.app.ConfirmActions
import com.leapcontrol.app.DoubleConfirmRules

/**
 * 「快捷控制再点一次」：勾选哪些控车动作需要确认。
 *
 * 只作用于**桌面小组件**与**控制中心磁贴**这两处 —— App 内下发前弹窗是另一个开关
 * （设置里的「控车二次确认」），两者互不覆盖，页面上也把这件事写清楚，
 * 免得用户在这里勾了半天，回首页发现照旧弹窗 / 反过来以为没生效。
 *
 * 默认一项都不勾：桌面和下拉控制中心存在的意义就是一步到位，需要防误触的人自己开。
 */
@Composable
fun ConfirmSettingsScreen(onBack: () -> Unit) {
    val C = LocalAppColors.current
    val context = LocalContext.current.applicationContext
    // prefs 不是可观察对象，用一份本地集合驱动重组；改完顺手回读一次，保证与磁盘一致
    var enabled by remember { mutableStateOf(ConfirmActions.enabled(context)) }

    // 这个页面是盖在「设置」上的整页，不拦返回的话侧滑会直接把 Activity 关了
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "快捷控制再点一次", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            Text(
                "勾上的动作，在桌面小组件和控制中心里第一次点只会提示，${DoubleConfirmRules.WINDOW_MS / 1000} 秒内再点一次才真正下发。",
                color = C.textPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "默认一项都不勾：点一次就生效。这里只作用于桌面与控制中心，App 内的「控车二次确认」是另一套。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction(
                    text = "全部开启",
                    highlight = enabled.isEmpty(),
                ) {
                    ConfirmActions.setAll(context, true)
                    enabled = ConfirmActions.enabled(context)
                }
                MiniAction(
                    text = "全部关闭",
                    highlight = enabled.isNotEmpty(),
                ) {
                    ConfirmActions.setAll(context, false)
                    enabled = ConfirmActions.enabled(context)
                }
            }
        }

        // 分组顺序由枚举声明决定（车门 → 后备箱 → 车窗 → 空调 → 遮阳帘 → 其他），
        // 这里不再写第二份分组表，改动作时只改一处
        ConfirmAction.groups().forEach { (group, actions) ->
            Spacer(Modifier.height(14.dp))
            SettingsCard(group) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    ConfirmActionRow(
                        label = action.title,
                        checked = action.command in enabled,
                        onCheckedChange = { on ->
                            ConfirmActions.setEnabled(context, action.command, on)
                            enabled = ConfirmActions.enabled(context)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "提示：确认窗口很短，过了就要重新点两下。车在地库信号差时，开得越多，操作越慢。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 一行动作开关：整行可点（只点开关那一小块对手指太苛刻）。 */
@Composable
private fun ConfirmActionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = C.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = C.barTrack,
            ),
        )
    }
}

/**
 * 小号文字按钮。
 *
 * [highlight] 只是把「现在点了有用」的那个提亮一档：全部开启后「全部开启」就没意义了，
 * 不必让它和旁边那个看起来一样重。
 */
@Composable
private fun MiniAction(
    text: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (highlight) C.textPrimary else C.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(C.chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
