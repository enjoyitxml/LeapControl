package com.leapcontrol.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * 「开源协议与致谢」页：写明二次开发所基于的开源项目、原作者与项目地址，
 * 说清协议与署名的处理方式，并引导用户同时支持原作者。
 */
object OpenSourceInfo {
    /** 原项目名称。 */
    const val PROJECT_NAME = "零跑智控（leapauto）"

    /** 原作者。 */
    const val AUTHOR = "stephenwang1011"

    /** 原项目地址。 */
    const val PROJECT_URL = "https://github.com/stephenwang1011/leapauto"

    const val LEAD =
        "零控不是从零写起的。正是站在原作者的肩膀上，才有了今天这个能每天顺手用一用的 App。"

    const val THANKS =
        "原作者凭一己之力啃下了零跑车云协议的梳理、BLE 无感蓝牙钥匙与国密鉴权这些难度极高的工作，" +
            "还把整套成果完整地开源出来。代码结构干净、接口注释详尽，连车型差异和信号边界都交代得" +
            "清清楚楚 —— 这份工程完成度和分享精神，都值得一句由衷的佩服。零控所做的，只是在这套" +
            "扎实的底座上，按自己的用车习惯把界面和交互重做了一遍罢了。"
}

@Composable
fun OpenSourceScreen(
    onBack: () -> Unit,
    onOpenSupport: () -> Unit = {},
) {
    val C = LocalAppColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "开源协议与致谢", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 开篇 ----
        SettingsCard {
            Text(
                OpenSourceInfo.LEAD,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
        }

        // ---- 原项目 ----
        Spacer(Modifier.height(12.dp))
        SettingsCard("原项目") {
            InfoRow(label = "项目名称", value = OpenSourceInfo.PROJECT_NAME)
            Spacer(Modifier.height(8.dp))
            InfoRow(label = "原作者", value = OpenSourceInfo.AUTHOR)
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "打开项目地址",
                desc = OpenSourceInfo.PROJECT_URL,
                iconRes = R.drawable.ic_opensource,
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(OpenSourceInfo.PROJECT_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        // ---- 致谢 ----
        Spacer(Modifier.height(12.dp))
        SettingsCard("致谢") {
            Text(
                OpenSourceInfo.THANKS,
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }

        // ---- 协议与署名 ----
        Spacer(Modifier.height(12.dp))
        SettingsCard("协议与署名") {
            ClauseList(
                listOf(
                    "本项目遵循原项目的开源协议，完整保留原作者署名与项目出处，未删除、未篡改任何版权与声明信息。",
                    "若原项目后续调整协议或授权方式，本项目同步遵从；原仓库未单独声明的部分，以原仓库说明为准。",
                    "二次开发部分（界面重构、功能模块自定义、四季主题等）同样免费开放给个人学习与自用，禁止用于任何商业牟利。",
                ),
            )
        }

        // ---- 免费与支持 ----
        Spacer(Modifier.height(12.dp))
        SettingsCard("免费与支持") {
            ClauseList(
                listOf(
                    "零控完全免费：无广告、无订阅、无强制收费，也不出售任何数据。",
                    "如果你认可这份工作，欢迎支持本人，也欢迎去原项目给原作者点个 Star —— 那是对开源最好的回报。",
                    "打赏纯属自愿赠与，不构成任何购买或服务关系，也不影响任何功能的使用。",
                ),
            )
        }

        // ---- 跳支持页 ----
        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "支持开发者",
            desc = "如果零控帮到了你，可以请作者喝杯咖啡",
            iconRes = R.drawable.ic_support,
            onClick = onOpenSupport,
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}
