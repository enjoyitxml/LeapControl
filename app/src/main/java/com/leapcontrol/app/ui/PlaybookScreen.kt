package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「玩法说明」页（首页右上角那个手柄图标进入）。
 *
 * 这里不做「功能清单」，只回答新用户最常问的几件事：车模怎么换、背景怎么换、
 * 怎么和官方 APP 同时登录、快捷控车怎么加、纯电为什么没续航、首页卡片怎么排、
 * 零记是什么。文案尽量给可执行的路径（哪个页面 → 哪个按钮），
 * 而不是泛泛的「可在设置中修改」。
 *
 * 图标可以在「个性化 → 首页图标」里关掉，但页面本身保留：
 * 关了入口不代表内容该消失，设置里仍可从这里之外的地方被再次打开。
 *
 * 第五节「零记」跟着零记总开关走：功能关掉之后这一页不该再教一个不存在的功能。
 */
@Composable
fun PlaybookScreen(
    onBack: () -> Unit,
    /** 零记功能是否开启；关掉时最后一节「零记」不再显示。 */
    journalEnabled: Boolean = true,
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
        SettingsTopBar(title = "玩法说明", onBack = onBack)
        Spacer(Modifier.height(6.dp))
        Text(
            "零控是个偏「自己用着顺手」的工具，说明书就这几条，看完基本不会再有「这功能在哪儿」的疑问。",
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        )
        Spacer(Modifier.height(10.dp))

        PlaybookSection(
            no = 1,
            title = "如何更换车模",
            body = "点击右上角「个性化」，下拉找到「车模外观」，这里有两个方案：\n\n" +
                "① 直接改色：软件内置了各色车漆，点一下就能换，立刻生效；\n" +
                "② AI 自定义涂装：先点「保存当前车模到相册」，把车模图发给豆包等 AI 软件，" +
                "告诉它「请帮我把这个车换成 XX 涂装」，生成好的图片再回到零控导入即可。",
            tip = "一定要关掉 AI 软件的水印，否则会影响车模的裁切。" +
                "例如豆包：进入设置 → 内容生成与产物设置 → AI 生成水印管理 → 关闭水印。",
        )

        Spacer(Modifier.height(12.dp))

        PlaybookSection(
            no = 2,
            title = "自定义背景",
            body = "零控提供了比较丰富的背景自定义：点界面上的小白点可以快速切换主题；" +
                "也可以在设置里改成「点车模 / 点卡片就换壁纸」。\n\n" +
                "四季主题、动态背景、外观模式（跟随系统 / 浅色 / 深色）都在「个性化」里，请自行探索。",
        )

        Spacer(Modifier.height(12.dp))

        PlaybookSection(
            no = 3,
            title = "零控和官方 APP 同时登录",
            body = "零控走的就是官方接口，所以同一个账号在零控登录之后，官方 APP 那边会被挤下线。\n\n" +
                "想两边同时在线，需要准备两个手机号：用副卡号注册一个零跑账号，" +
                "让车主把车辆共享给副卡号，然后用副卡号登录零控。\n\n" +
                "① 车主账号打开零跑官方 APP → 爱车 → 右上角设置 → 我的车辆 → 点击车辆 → 添加共享成员；\n" +
                "② 切换到副卡账号，确认接受邀请；\n" +
                "③ 用副卡账号登录零控即可，主号继续留在官方 APP 上。",
            tip = "两个账号控的是同一台车，数据不会打架。" +
                "副卡号只用来登录零控，别在官方 APP 上也登一次，否则又会把另一头挤掉。",
        )

        Spacer(Modifier.height(12.dp))

        PlaybookSection(
            no = 4,
            title = "如何快捷控车",
            body = "下拉手机控制中心，点「编辑」，找到零控的快捷控车按钮添加进去。" +
                "之后不用打开 App 就能直接控车，而且控车无需密码验证，享受畅快体验。\n\n" +
                "桌面小组件功能已经删掉了 —— 前后做了好几版，安卓原生小组件的效果始终达不到我的审美要求，" +
                "干脆直接去掉，把精力放在控制中心磁贴上。",
        )

        Spacer(Modifier.height(12.dp))

        PlaybookSection(
            no = 5,
            title = "纯电为什么显示不了续航",
            body = "个别纯电车型（比如 C01 纯电）没有上报标准的续航字段，" +
                "零控按默认规则去认，就会认成「--」。\n\n" +
                "遇到这种情况，到「设置 → 动力类型」手动选成「纯电」，续航就会正常显示。" +
                "其它车型出现同样的问题，也在这里手动指定一次即可。",
            tip = "动力类型是按车辆分别记的，换车、换账号都不会丢；" +
                "选错了改回「自动识别」就行。",
        )

        Spacer(Modifier.height(12.dp))

        PlaybookSection(
            no = 6,
            title = "首页小卡片",
            body = "首页卡片支持自定义排序、任意添加删除：滑到卡片区最下面点「编辑」，" +
                "长按卡片拖动排序，点卡片右上角的叉删掉，底部的「添加卡片」再挑回来，改完点「完成」。\n\n" +
                "后续可能会上架更多小卡片。",
        )

        // 最后一节只在零记开着的时候出现：功能关掉后还教人怎么用，只会点了个寂寞
        if (journalEnabled) {
            Spacer(Modifier.height(12.dp))

            PlaybookSection(
                no = 7,
                title = "零记",
                body = "零记是我在原作者版本的基础上加进来的记录功能：" +
                    "可以设定用车目标、记录日常用车成本、打卡去过的城市，也会提醒保养与保险到期。\n\n" +
                    "不想用的话可以在「设置 → 零记」里整个关掉，底部那一格和首页的零记卡都会收起。" +
                    "如果你有更好玩、更有趣的想法，" +
                    "可以在「我的 → 支持开发者」里找到我，或者到车友群里说一声。",
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "本软件仅供车友自用交流，请勿外传或二次分发。",
            color = C.textSecondary.copy(alpha = 0.75f),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * 一条玩法：序号 + 标题 + 正文，正文下面可以挂一条「注意」提示。
 *
 * 用实心卡片而不是可折叠条目：这几条都不长，全部展开也只是两屏左右，
 * 折叠反而让人多点好几次。
 */
@Composable
private fun PlaybookSection(
    no: Int,
    title: String,
    body: String,
    tip: String? = null,
) {
    val C = LocalAppColors.current
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp)
                    .background(C.accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = no.toString(),
                    color = C.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
        if (tip != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.accent.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                // 竖条 + 文字：比整块高饱和底色更克制，压在白卡片上不会跳
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(C.accent, RoundedCornerShape(1.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tip,
                    color = if (C.isDark) C.textPrimary else C.accent,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
