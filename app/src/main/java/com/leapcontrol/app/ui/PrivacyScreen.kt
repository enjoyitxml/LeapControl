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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「隐私条款」页：说明软件仅作者与好友自用、数据全部留在本地、不收集隐私。
 */
object PrivacyText {
    const val LEAD =
        "隐私不是选项，而是底线。零控从第一行代码开始，就没打算拿走你的任何东西。"

    val SECTIONS: List<Pair<String, List<String>>> = listOf(
        "一、仅限自用" to listOf(
            "零控是作者本人开发、供自己和好友使用的工具：无广告、无订阅、无内购，不对外提供下载，也不提供任何收费服务。",
            "所有功能一视同仁，没有\"解锁高级功能\"的付费墙，也没有需要付费才能用的部分。",
            "软件不面向公众发布，因此不承担任何形式的对外服务承诺。",
        ),
        "二、数据全部存在本地" to listOf(
            "登录凭证、控车密码、自定义车名、快捷按钮与模块排序等，全部保存在你手机本地的 App 私有目录，不会上传到任何服务器。",
            "控车密码使用 Android Keystore 硬件密钥 + AES-GCM 加密存储，App 自身也无法导出明文。",
            "车况数据只在需要展示时读取，用完即弃，不常驻后台、不做任何形式的留存与转发。",
            "卸载 App 会一并清除全部本地数据；服务器侧不存在属于零控的账号数据。",
        ),
        "三、不收集任何隐私" to listOf(
            "不收集、不上传手机号、通讯录、短信、相册、定位、设备标识符等个人敏感信息。",
            "不采集车辆位置轨迹：车辆经纬度仅在内存中按需驻留，绝不写入日志，也绝不上传。",
            "不集成任何第三方统计、广告、行为埋点或云端崩溃上报 SDK —— 崩溃信息只在本机弹给你自己看。",
            "不做后台常驻、不做静默唤醒，除了你主动发起的操作，App 不会在后台偷偷联网。",
        ),
        "四、联网只做这几件事" to listOf(
            "登录与验证码：直连零跑官方服务，仅用于校验你的车主身份。",
            "车况读取与远程控车：指令经零跑官方云端接口下发，路径与官方 App 完全一致。",
            "车模图片、行驶里程与能耗统计：同样取自零跑官方接口。",
            "目的地搜索：仅在「发送到车机」时使用，且依赖你自行填写的高德 Web Key；不填则完全不发起该请求。",
        ),
        "五、你的权利" to listOf(
            "你可以随时删除本地数据、退出登录或卸载 App，无需任何申请。",
            "若本条款后续有调整，会在这个页面同步更新说明。",
            "如发现任何与本条款不符的行为，请立即卸载并停止使用 —— 那一定不是本软件的预期行为。",
        ),
    )
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
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
        SettingsTopBar(title = "隐私条款", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 开篇 ----
        SettingsCard {
            Text(
                PrivacyText.LEAD,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
        }

        // ---- 五个章节 ----
        PrivacyText.SECTIONS.forEach { (title, items) ->
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Text(
                    title,
                    color = C.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                ClauseList(items)
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}
