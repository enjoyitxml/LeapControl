package com.leapcontrol.app.ui

/**
 * 对外联系方式 / 入口常量。
 *
 * 即时通讯类的联系入口已全部下线，App 内不再出现这类说法；
 * 对外反馈只走下面的问卷链接。
 */

/**
 * 意见反馈问卷地址（腾讯问卷）。
 *
 * 「我的 → 意见反馈」用系统浏览器打开，不在 App 内嵌 WebView：
 * 问卷需要登录第三方账号，内置内核拿不到系统账号，用户会被卡在登录页。
 * 换问卷时只改这里，页面代码不用动。
 */
internal const val FEEDBACK_URL = "https://wj.qq.com/s2/28445754/rmv1/"
