package com.leapcontrol.app.ui

/**
 * QQ 交流群群号。
 *
 * 两处在用：「我的」页页脚（点击复制群号）与更新弹窗（下载不动时的兜底通道）。
 * 更新包走 GitHub Releases，国内网络偶尔很慢，所以群是「拿不到安装包」时的最后一条路，
 * 弹窗里必须能直接看到群号，不能只写「加群」两个字让用户自己去别处找。
 */
internal const val QQ_GROUP = "179731222"

/**
 * 意见反馈问卷地址（腾讯问卷）。
 *
 * 「我的 → 意见反馈」用系统浏览器打开，不在 App 内嵌 WebView：
 * 问卷需要登录 QQ / 微信态，内置内核拿不到系统账号，用户会被卡在登录页。
 * 换问卷时只改这里，页面代码不用动。
 */
internal const val FEEDBACK_URL = "https://wj.qq.com/s2/28445754/rmv1/"
