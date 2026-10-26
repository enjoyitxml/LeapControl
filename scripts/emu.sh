#!/usr/bin/env bash
# ============================================================
# 零控（LeapControl）本机模拟器工具
#
#   ./scripts/emu.sh            全链路：启动模拟器 → 构建 → 安装 → 启动 App
#   ./scripts/emu.sh start      只启动模拟器并等它就绪
#   ./scripts/emu.sh stop       关闭模拟器
#   ./scripts/emu.sh status     看模拟器 / App 状态
#   ./scripts/emu.sh shot       截一张当前屏幕到项目根
#   ./scripts/emu.sh log        看 App 的实时日志（Ctrl+C 退出）
#   ./scripts/emu.sh install xx.apk   单独安装某个 APK
#   ./scripts/emu.sh devices    列出所有已连接的设备
#   ./scripts/emu.sh test       跑 JVM 单元测试（不需要模拟器或手机）
#
# 插着开了 USB 调试的手机时，自动优先用真机（模拟器会被跳过）；
# 不插手机则自动启动模拟器。
#
# 可用参数：
#   --no-build   跳过构建，直接装已有的 APK
#   --cold       冷启动（丢弃快照，每次都是干净的开机）
#   --software   退回纯 CPU 软件渲染（本机 GPU 加速出问题时用，会明显变卡）
# ============================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTER_DIR="$(dirname "$PROJECT_DIR")"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
COPY_APK="$OUTER_DIR/零控-debug.apk"
PKG="com.leapbuddy.app"
AVD_NAME="${AVD_NAME:-LeapControl_Test}"
LOG="/tmp/leapcontrol-emulator.log"
IMG_TAG="system-images;android-35;google_apis;x86_64"
DEVICE="pixel_7"

# ---------- 定位 SDK ----------
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    SDK="$ANDROID_HOME"
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    SDK="$ANDROID_SDK_ROOT"
elif [ -f "$PROJECT_DIR/local.properties" ]; then
    SDK="$(sed -n 's/^sdk\.dir=//p' "$PROJECT_DIR/local.properties" | sed 's/\\\\:/:/g')"
fi
SDK="${SDK:-$HOME/Library/Android/sdk}"
if [ ! -d "$SDK" ]; then
    echo "✗ 找不到 Android SDK：$SDK"
    echo "  请先安装 Android Studio，或设置 ANDROID_HOME"
    exit 1
fi
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"

# ---------- 小工具 ----------
info() { printf '\033[36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[33m! %s\033[0m\n' "$*"; }
die()  { printf '\033[31m✗ %s\033[0m\n' "$*"; exit 1; }

serial() { adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1; exit}'; }

# 目标设备：真机（USB / 无线）优先，没有真机才退回模拟器
pick_serial() {
    local all s
    all="$(adb devices | awk '$2=="device" {print $1}')"
    s="$(awk '!/^emulator-/ {print; exit}' <<< "$all")"
    [ -n "$s" ] || s="$(awk '/^emulator-/ {print; exit}' <<< "$all")"
    printf '%s' "$s"
}

# ---------- 模拟器 ----------
ensure_avd() {
    if [ -d "$HOME/.android/avd/$AVD_NAME.avd" ]; then return 0; fi
    info "AVD「$AVD_NAME」不存在，正在创建…"
    if [ ! -d "$SDK/$IMG_TAG" ]; then
        info "下载系统镜像 $IMG_TAG（约 3.5G，需要几分钟）"
        yes | sdkmanager "$IMG_TAG" >/dev/null 2>&1 || true
    fi
    [ -d "$SDK/$IMG_TAG" ] || die "系统镜像缺失，请手动执行：sdkmanager \"$IMG_TAG\""
    echo no | avdmanager create avd \
        --name "$AVD_NAME" \
        --package "$IMG_TAG" \
        --device "$DEVICE" \
        --sdcard 512M \
        --force >/dev/null 2>&1 || die "AVD 创建失败"
    ok "AVD「$AVD_NAME」已创建"
}

emulator_up() {
    # 有设备且 package 服务可用才算真正就绪
    local s; s="$(serial)"
    [ -n "$s" ] || return 1
    adb -s "$s" shell cmd package list packages >/dev/null 2>&1
}

wait_ready() {
    info "等待模拟器就绪（首次开机较慢）…"
    local i=0
    while [ $i -lt 180 ]; do
        if emulator_up; then
            local s; s="$(serial)"
            # 等桌面起来再继续
            adb -s "$s" shell getprop sys.boot_completed >/dev/null 2>&1
            ok "模拟器就绪：$s"
            return 0
        fi
        sleep 2
        i=$((i + 1))
    done
    die "等了 6 分钟模拟器还没起来，看日志：$LOG"
}

cmd_start() {
    # 已经插着真机就没必要起模拟器
    local real; real="$(adb devices | awk '$2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
    if [ -n "$real" ]; then
        ok "检测到真机 $real，直接用它"
        return 0
    fi
    if emulator_up; then
        ok "模拟器已经在跑：$(serial)"
        return 0
    fi
    ensure_avd
    # 渲染交给 AVD 配置（hw.gpu.enabled=yes + hw.gpu.mode=host，走本机 GPU，比软件渲染流畅得多）。
    # 万一某台机器上 host 模式出问题，用 --software 退回纯 CPU 渲染。
    local opts="-no-audio -no-boot-anim"
    grep -q -- --cold <<< "$*" && opts="$opts -no-snapshot-load"
    grep -q -- --software <<< "$*" && opts="$opts -gpu swiftshader_indirect"
    info "启动模拟器 $AVD_NAME"
    nohup "$SDK/emulator/emulator" -avd "$AVD_NAME" $opts >"$LOG" 2>&1 &
    disown 2>/dev/null || true
    # 等 adb 端口出现
    local i=0
    while [ $i -lt 120 ] && [ -z "$(serial)" ]; do sleep 1; i=$((i + 1)); done
    wait_ready
}

cmd_stop() {
    local s; s="$(serial)"
    if [ -n "$s" ]; then
        adb -s "$s" emu kill >/dev/null 2>&1
        ok "模拟器已关闭"
    else
        warn "模拟器没在跑"
    fi
}

cmd_status() {
    adb devices | grep -q unauthorized && warn "手机上还没授权，请在手机上点「允许 USB 调试」"
    local s; s="$(pick_serial)"
    if [ -n "$s" ]; then
        local kind="模拟器"; [[ "$s" != emulator-* ]] && kind="真机"
        ok "${kind} ${s}：$(adb -s "$s" shell getprop ro.product.model 2>/dev/null) / Android $(adb -s "$s" shell getprop ro.build.version.release 2>/dev/null) / $(adb -s "$s" shell getprop ro.product.cpu.abi 2>/dev/null)"
    else
        warn "没有可用设备（模拟器未启动，也没插真机）"
        return 0
    fi
    if adb -s "$s" shell ps -A 2>/dev/null | grep -q "$PKG"; then
        ok "App 正在运行：$(adb -s "$s" shell dumpsys activity activities 2>/dev/null | grep -m1 -o "$PKG/[.A-Za-z]*")"
    else
        warn "App 未运行"
    fi
}

# ---------- 构建 / 安装 ----------
cmd_build() {
    info "构建 Debug 包（JDK 21）"
    ( cd "$PROJECT_DIR" && JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
        ./gradlew :app:assembleDebug --no-daemon -q ) || die "构建失败"
    [ -f "$APK" ] || die "没找到产物：$APK"
    cp -f "$APK" "$COPY_APK"
    ok "构建完成 → $COPY_APK"
}

cmd_install() {
    local apk="${1:-}"
    # 优先用刚构建出来的产物，其次 release 目录里最新的，最后才是外层副本
    [ -n "$apk" ] || [ ! -f "$APK" ] || apk="$APK"
    [ -n "$apk" ] || apk="$(ls -t "$PROJECT_DIR"/release/*.apk 2>/dev/null | head -1)"
    [ -n "$apk" ] && [ -f "$apk" ] || apk="$COPY_APK"
    [ -f "$apk" ] || die "没有可安装的 APK"
    local s; s="$(pick_serial)"
    [ -n "$s" ] || die "没有可用设备：先 ./scripts/emu.sh start，或插上开好 USB 调试的手机"
    info "安装 $(basename "$apk") → $s"
    local out=""
    for i in 1 2 3; do
        out="$(adb -s "$s" install -r "$apk" 2>&1)"
        grep -q "^Success$" <<< "$out" && { ok "安装完成"; return 0; }
        # 设备刚开机时 StorageManager 还没就绪，会抛 NPE，等一下重试即可
        [ $i -lt 3 ] && { warn "第 $i 次未成功，3 秒后重试"; sleep 3; }
    done
    printf '%s\n' "$out" | head -5 | sed 's/^/   /'
    die "安装失败"
}

cmd_launch() {
    info "启动零控"
    local s; s="$(pick_serial)"
    [ -n "$s" ] || die "没有可用设备"
    adb -s "$s" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 \
        || adb -s "$s" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 3
    if adb -s "$s" shell ps -A 2>/dev/null | grep -q "$PKG"; then
        ok "零控已运行"
    else
        warn "没检测到进程，看看日志：./scripts/emu.sh log"
    fi
}

cmd_shot() {
    local s; s="$(pick_serial)"
    [ -n "$s" ] || die "没有可用设备"
    local name="零控-$(date +%m%d-%H%M%S).png"
    adb -s "$s" exec-out screencap -p > "$OUTER_DIR/$name"
    ok "截图 → $name"
}

cmd_log() {
    local s; s="$(pick_serial)"
    [ -n "$s" ] || die "没有可用设备"
    info "实时日志（Ctrl+C 退出）"
    adb -s "$s" logcat -v brief --pid "$(adb -s "$s" shell ps -A | awk -v p="$PKG" '$0 ~ p {print $2; exit}')"
}

cmd_devices() {    echo "连接到的设备："
    adb devices -l | awk 'NR>1 && NF {print "   "$0}'
}

# ---------- 单元测试 ----------
cmd_test() {
    info "跑 JVM 单测（不需要设备）"
    ( cd "$PROJECT_DIR" && JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
        ./gradlew :app:testDebugUnitTest --no-daemon ) || die "单测失败，看上面的报错"
    ok "单测全部通过（报告在 app/build/reports/tests/）"
}

# ---------- 入口 ----------
ARGS=("$@")
SUB="${1:-}"
[ "${SUB:0:2}" = "--" ] && SUB=""
[ -n "$SUB" ] && ARGS=("${@:2}")

case "$SUB" in
    start)   cmd_start "${ARGS[@]}" ;;
    stop)    cmd_stop ;;
    status)  cmd_status ;;
    shot)    cmd_shot ;;
    log)     cmd_log ;;
    install) cmd_install "${ARGS[0]:-}" ;;
    devices) cmd_devices ;;
    test)    cmd_test ;;
    ""|run)
        grep -q -- --no-build <<< "${ARGS[*]}" || cmd_build
        cmd_start
        cmd_install
        cmd_launch
        ok "全部完成，去看设备屏幕吧"
        ;;
    *)
        sed -n '2,21p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        ;;
esac
