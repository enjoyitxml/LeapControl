package com.leapcontrol.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 冷启动首帧缓存链路的守门测试。
 *
 * 这两处都**编译得过、跑起来也不会报错**，删掉只是"首页第一帧会闪一下"——
 * 恰恰是最容易被后续重构顺手抹掉的那类代码：
 * - 车模：`CarImagePreload.warm()` 预热 + `HomeScreen` 的固定占位与淡入；
 * - 小地图：`VehiclePlaceMemory.seedFromDisk()` 在冷启动时同步补上磁盘里那张图。
 *
 * 真正的效果得真机冷启动看，所以这里只钉住"接线还在"。
 */
class FirstFrameCacheTest {

    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app").isDirectory }

    private fun read(path: String) = File(projectDir, path).readText()

    @Test
    fun `冷启动预热：Application 启动时就解码车模，登录成功后再补一次`() {
        val app = read("app/src/main/java/com/leapcontrol/app/LeapApplication.kt")
        assertTrue(
            "LeapApplication 不再预热车模 —— 冷启动第一帧会退回空白",
            app.contains("CarImagePreload.warm("),
        )
        val root = read("app/src/main/java/com/leapcontrol/app/ui/LeapControlApp.kt")
        assertTrue(
            "登录成功后要补一次预热：首次登录时 Application 那次读不到 VIN",
            root.contains("CarImagePreload.warm(context)"),
        )
        assertTrue(
            "登出必须清掉预热图，否则下一位车主首帧会看到上一个人的车",
            root.contains("CarImagePreload.clear()"),
        )
    }

    @Test
    fun `车模首帧兜底：runtime 两层缓存都空时取预热图`() {
        val runtime = read("app/src/main/java/com/leapcontrol/app/ui/AppRuntime.kt")
        assertTrue(
            "cachedDisplayCarBitmap 少了预热兜底，冷启动首帧又是空的",
            runtime.contains("CarImagePreload.peek(session.selectedVin)"),
        )
    }

    @Test
    fun `车模容器恒定占位并淡入，不把下方内容挤动`() {
        val home = read("app/src/main/java/com/leapcontrol/app/ui/HomeScreen.kt")
        assertTrue(
            "是否留位要看磁盘上有没有车模文件",
            home.contains("runtime.hasCustomCarModel() || runtime.hasOfficialCarImage()"),
        )
        assertTrue(
            "占位判断必须同时认「图已在」「文件在」「3D 已就绪」三种情况",
            home.contains("if (show3D || carBitmap != null || hasCarFile)"),
        )
        assertTrue(
            "2D 车模淡入丢了，图后到时又是「啪」一下",
            home.contains("alpha = carAlpha * (1f - car3DAlpha)"),
        )
        assertTrue(
            "3D 车模要自己的淡入，否则包就绪那一下是从空场景硬切",
            home.contains("alpha = car3DAlpha"),
        )
    }

    @Test
    fun `小地图冷启动直接读磁盘缓存当首帧`() {
        val card = read("app/src/main/java/com/leapcontrol/app/ui/VehicleLocationCard.kt")
        assertTrue(
            "rememberVehiclePlace 少了种子读取，冷启动第一帧会退回「加载中」",
            card.contains("VehiclePlaceMemory.seedFromDisk(context, full, dark)"),
        )
        val memory = read("app/src/main/java/com/leapcontrol/app/ui/VehiclePlaceMemory.kt")
        assertTrue(
            "图片归属必须校验：只比坐标会把上一处的图钉在当前地址上",
            memory.contains("VehicleLocationRules.mapMatches(place, place.latitude, place.longitude)"),
        )
        assertTrue(
            "每个槽只读一次盘，不能每次重组都同步读",
            memory.contains("seededFromDisk"),
        )
    }
}
