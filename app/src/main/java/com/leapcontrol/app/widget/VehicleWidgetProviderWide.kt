package com.leapcontrol.app.widget

/**
 * 5×2 宽版桌面小组件的 broadcast receiver。
 *
 * 一个 receiver 对应「添加小部件」里的一个条目、一份 `res/xml/widget_info_*`，
 * 所以想要 5 列版就只能再注册一个（见 AndroidManifest 与 `widget_info_wide.xml`）。
 *
 * 这里**故意不重写任何方法**：控车、二次确认、刷新、结果提示全都走父类，
 * 两个尺寸之间不存在「行为不一致」的可能。唯一的分支在 [VehicleWidget.render]：
 * 按 component 判断该用 `widget_*_wide` 还是 `widget_*` 那份布局。
 *
 * 别把它改成 `object` / 顶层函数 —— AppWidgetProvider 必须是一个可实例化的 BroadcastReceiver，
 * 系统按 component 名反射创建。
 */
class VehicleWidgetProviderWide : VehicleWidgetProvider()
