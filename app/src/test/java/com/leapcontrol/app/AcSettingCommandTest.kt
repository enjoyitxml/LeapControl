package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空调「整组参数」指令：`acSetting:...` 合成命令名与 170 指令之间的往返。
 *
 * 这一层是展开面板所有风量 / 出风方向 / 循环调整的唯一出口，一旦字段拼错
 * 车端会把没带的字段按默认值重置（例如调风量把温度打回 24℃），所以字段映射逐个断言。
 */
class AcSettingCommandTest {

    private fun state(name: String): JSONObject = JSONObject(Commands.build(name).stateJson)

    @Test
    fun `调风量时其余字段原样带下去`() {
        val cmd = Commands.build(
            Commands.acSettingName(
                op = "wind",
                temperature = 26,
                windLevel = 4,
                mode = "cold",
                circle = "out",
                windshield = true,
                position = "wshld",
            )
        )
        assertEquals("170", cmd.cmdid)
        val o = JSONObject(cmd.stateJson)
        assertEquals("manual", o.getString("operate"))
        assertEquals("26", o.getString("temperature"))
        assertEquals("4", o.getString("windlevel"))
        assertEquals("cold", o.getString("mode"))
        assertEquals("out", o.getString("circle"))
        assertEquals("1", o.getString("wshld"))
        assertEquals("wshld", o.getString("position"))
        assertEquals("风量调到 4 挡", cmd.label)
    }

    @Test
    fun `出风方向与循环的操作文案`() {
        assertEquals(
            "出风改到前挡",
            Commands.build(
                Commands.acSettingName("position", 24, 2, position = "wshld", windshield = true)
            ).label,
        )
        assertEquals(
            "出风改到全车",
            Commands.build(Commands.acSettingName("position", 24, 2, position = "all")).label,
        )
        assertEquals(
            "切到外循环",
            Commands.build(Commands.acSettingName("circle", 24, 2, circle = "out")).label,
        )
        assertEquals(
            "切到内循环",
            Commands.build(Commands.acSettingName("circle", 24, 2, circle = "in")).label,
        )
    }

    @Test
    fun `合成命令名里的非法值被夹到合法范围`() {
        // 越界温度、0 挡风、未知模式、乱七八糟的循环 / 风向 / 开关值
        val o = state("acSetting:op=wind,temperature=99,windlevel=0,mode=bad,circle=x,wshld=7,position=zzz")
        assertEquals("32", o.getString("temperature"))
        assertEquals("1", o.getString("windlevel"))
        assertEquals("nohotcold", o.getString("mode"))
        assertEquals("in", o.getString("circle"))
        assertEquals("0", o.getString("wshld"))
        assertEquals("all", o.getString("position"))
    }

    @Test
    fun `构造时就把越界风量夹住`() {
        val name = Commands.acSettingName("wind", 24, 99)
        assertTrue(name.contains("windlevel=7"))
        val nameLow = Commands.acSettingName("wind", 24, -3)
        assertTrue(nameLow.contains("windlevel=1"))
    }

    @Test
    fun `参数缺失时退回默认值而不是崩`() {
        val o = state("acSetting:op=wind")
        assertEquals("24", o.getString("temperature"))
        assertEquals("2", o.getString("windlevel"))
        assertEquals("manual", o.getString("operate"))
        assertEquals("all", o.getString("position"))
    }

    @Test
    fun `快捷场景与座椅类命令仍然可用`() {
        assertEquals("170", Commands.build("quickCool").cmdid)
        assertEquals("170", Commands.build("defrost").cmdid)
        assertEquals("301", Commands.build("driverSeatHeating_2").cmdid)
        assertEquals("370", Commands.build("passengerSeatVentilation_1").cmdid)
        assertEquals("320", Commands.build("steeringWheelHeating_1").cmdid)
        assertEquals("440", Commands.build("rearviewMirrorHeating_on").cmdid)
    }

    /**
     * 除霜那一组参数逐字段钉死。
     *
     * 这是车端唯一认的组合：mode=cold 才会开压缩机除湿，circle=out 才不会把车内湿气
     * 反复吹到玻璃上，wshld=1 + position=wshld 才同时点亮「除霜位」和「前挡出风」。
     * 少一个字段车端就按默认值重置，用户看到的就是「开成了普通空调」。
     */
    @Test
    fun `前挡除霜下发的是完整的除霜组`() {
        val cmd = Commands.build("defrost")
        val o = JSONObject(cmd.stateJson)
        assertEquals("170", cmd.cmdid)
        assertEquals("manual", o.getString("operate"))
        assertEquals("cold", o.getString("mode"))
        assertEquals("out", o.getString("circle"))
        assertEquals("1", o.getString("wshld"))
        assertEquals("wshld", o.getString("position"))
        // 7 个字段一个都不能少：车端缺字段会按默认值重来
        assertEquals(7, o.length())
    }

    @Test
    fun `切换内外循环不会顺手改掉温度与风量`() {
        // 空调中心页的循环按钮按当前车况整组合成，26℃ / 4 挡必须原样带下去；
        // 老的 circleIn / circleOut 常量写死 24℃ / 7 挡，切个循环会把温度风量一起改掉。
        val name = Commands.acSettingName(
            op = "circle",
            temperature = 26,
            windLevel = 4,
            mode = "cold",
            circle = "out",
            windshield = false,
            position = "all",
        )
        val o = JSONObject(Commands.build(name).stateJson)
        assertEquals("26", o.getString("temperature"))
        assertEquals("4", o.getString("windlevel"))
        assertEquals("out", o.getString("circle"))
        assertEquals("cold", o.getString("mode"))
        assertEquals("切到外循环", Commands.build(name).label)
    }

    @Test
    fun `除霜状态下切循环会保留除霜位`() {
        val o = JSONObject(
            Commands.build(
                Commands.acSettingName(
                    op = "circle",
                    temperature = 24,
                    windLevel = 5,
                    mode = "cold",
                    circle = "in",
                    windshield = true,
                    position = "wshld",
                )
            ).stateJson
        )
        assertEquals("in", o.getString("circle"))
        assertEquals("1", o.getString("wshld"))
        assertEquals("wshld", o.getString("position"))
    }
}
