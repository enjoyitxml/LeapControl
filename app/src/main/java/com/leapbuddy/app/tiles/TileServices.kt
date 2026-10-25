package com.leapbuddy.app.tiles

/** 上锁 */
class LockTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "lock" }
}

/** 解锁 */
class UnlockTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "unlock" }
}

/** 鸣笛闪灯寻车 */
class HornTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "horn" }
}

/** 空调（按车况开/关） */
class ClimateTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "ac" }
}

/** 后备箱（按车况开/关） */
class TrunkTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "trunk" }
}

/** 车窗（按车况半开/全关） */
class WindowTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "window" }
}

/** 电池预热 */
class BatteryHeatTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "batteryPreheat" }
}

/** 天幕遮阳帘（按车况开/关） */
class SunshadeTileService : VehicleTileService() {
    override val spec = QuickTiles.ALL.first { it.id == "sunshade" }
}
