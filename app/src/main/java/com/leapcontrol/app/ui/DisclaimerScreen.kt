package com.leapcontrol.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 免责声明正文。
 *
 * 放在这里作为唯一数据源：页面、以及以后可能加的「首次启动强制阅读」弹窗都读它，
 * 避免同一份法律文本在两处各写一遍、改的时候漏改。
 */
object DisclaimerText {
    /** 声明全称（页面的副标题）。 */
    const val FULL_TITLE = "第三方零跑控车软件（零控LeapControl）免责声明"

    /** 开篇告知。 */
    const val PREAMBLE =
        "尊敬的用户，在使用本软件前，请您认真阅读并完全同意以下全部条款。" +
            "您下载、安装、使用本软件，即代表您已充分知晓、理解并自愿接受本免责声明所有内容。"

    /** 结语。 */
    const val CLOSING = "凡使用本软件，即视为已完全知悉风险并自愿全部免责。"

    /** 章节：标题 + 条款列表。 */
    data class Section(val title: String, val items: List<String>)

    val SECTIONS: List<Section> = listOf(
        Section(
            "一、软件性质说明",
            listOf(
                "本软件为个人开发的第三方辅助控车工具，非零跑汽车官方出品、非官方授权、非官方关联软件。",
                "本软件仅为用户提供便捷控车、车况查看、自定义辅助功能，所有车辆底层权限、车辆协议均来源于用户设备本地合法授权，不破解、不篡改、不绕过官方安全机制。",
                "本软件与零跑官方品牌、公司、系统无任何隶属、授权、合作关系，所有功能仅供车主个人学习、自用、体验。",
            ),
        ),
        Section(
            "二、使用风险告知",
            listOf(
                "车辆智能控制存在网络延迟、协议波动、车辆休眠、系统兼容、官方接口变更等不可控风险，用户明确知晓：控车操作存在失效、延迟、异常的可能性。",
                "用户严禁在车辆行驶过程中进行任何远程控车操作，严禁违规、危险、非法场景下使用本软件，所有危险操作后果由用户自行承担。",
                "因官方 OTA 升级、车辆系统更新、官方协议调整，可能导致本软件功能临时失效、报错、闪退，开发者不承诺永久可用、不承诺无条件更新维护。",
            ),
        ),
        Section(
            "三、责任完全免除",
            listOf(
                "在法律允许的最大范围内，本软件开发者不对任何直接或间接损失承担任何责任，包括但不限于：车辆功能异常、系统报错、数据异常、电池损耗、车辆故障、财产损失、出行延误、个人损失等。",
                "因用户违规使用、不当操作、私自修改参数、非法用途、未遵守交通法规产生的一切后果，均由用户本人独立承担全部法律与经济责任。",
                "因网络故障、运营商问题、设备故障、车辆自身故障、不可抗力导致的功能异常、操作失败，开发者不承担任何赔偿与修复责任。",
            ),
        ),
        Section(
            "四、合规与使用约定",
            listOf(
                "用户承诺：仅用于本人名下合法车辆，严禁用于盗车、非法控制、入侵、窃取数据、商业牟利等违法用途，违者自行承担法律责任。",
                "本软件不收集、不上传、不窃取用户隐私、车辆隐私、个人敏感数据，所有操作数据仅在用户本地设备运行。",
                "如当地法规、车企规则禁止第三方辅助工具使用，用户需立即自行卸载停用，开发者无任何强制责任。",
            ),
        ),
        Section(
            "五、其他说明",
            listOf(
                "本软件完全免费自用，无任何强制收费、捆绑服务，用户自愿选择使用。",
                "开发者有权随时更新、停用、下架、终止软件服务，无需提前通知用户。",
                "若您不同意本声明任意条款，请立即卸载、停止使用本软件。",
            ),
        ),
    )
}

/**
 * 「免责声明」页（从「我的」进入）。
 * 版式与「关于」页一致：顶栏 + 卡片，章节标题主色、正文次级色。
 */
@Composable
fun DisclaimerScreen(onBack: () -> Unit) {
    val C = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "免责声明", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 开篇告知 ----
        SettingsCard {
            Text(
                DisclaimerText.FULL_TITLE,
                color = C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                DisclaimerText.PREAMBLE,
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }

        // ---- 五个章节 ----
        DisclaimerText.SECTIONS.forEach { section ->
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Text(
                    section.title,
                    color = C.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                section.items.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(7.dp))
                    ClauseRow(index = index + 1, text = item)
                }
            }
        }

        // ---- 结语 ----
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            Text(
                DisclaimerText.CLOSING,
                color = C.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 单条条款：左「N.」序号，右正文（序号与正文基线对齐，换行时缩进对齐到正文）。 */
@Composable
fun ClauseRow(
    index: Int,
    text: String,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$index.",
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
