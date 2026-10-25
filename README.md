# 零控（LeapBuddy）

零跑汽车第三方控车 Android App，包名 `com.leapbuddy.app`。自用性质，**私有仓库**。

## 功能概览

- **首页**：总续航（纯电单条 / 增程电量+油量双进度条）、车辆状态（行驶状态、闭锁、车窗、后备箱、遮阳帘）
- **车模**：官方接口下发的定妆照，取不到时不占位，可在设置里整体关闭
- **快捷控车**：一行横滑（解锁 / 上锁 / 后备箱 / 车窗 / 鸣笛 / 电池预热 / 遮阳帘），顺序可自定义
- **充电中心**：大字 SOC + 健康充电 / 充电上限 / 预约充电 / 预约电池预热 / 绑桩，可下发到车端
- **空调模块**：开关空调、±调温（16~32°C）、车内实时温度
- **位置模块**：车辆地址逆地理解析，点击拉起地图导航
- 多车切换、四季主题、深浅配色、系统控制中心磁贴

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | **21**（构建必须指定，否则 AGP 8.7 会报错） |
| Android SDK | compileSdk 35 / buildTools 35.0.0 |
| 最低系统 | Android 8.0（API 26） |

`local.properties` 里的 `sdk.dir` 是本机路径，**已被 git 忽略**，不会上传。

## 常用命令

```bash
cd LeapBuddy

# 构建调试包
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --no-daemon -q

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

日常最省事的还是用发版脚本（见下），它会负责改版本号、构建、写变更记录、打标签。

---

## 版本管理规范

### 1. 版本号：语义化版本 X.Y.Z

| 位 | 什么时候进位 | 例子 |
|---|---|---|
| **X** 主版本 | 架构级重构、不兼容的大改 | 0.1.0 → 1.0.0 |
| **Y** 次版本 | 加了新功能 | 加胎压模块 → 0.2.0 |
| **Z** 修订号 | 修 bug、UI 微调、文案改动 | 调间距 → 0.1.1 |

`versionCode` 不必手动管，脚本按 `X*10000 + Y*100 + Z` 自动生成（0.1.0 → 100），保证单调递增，Android 系统会正确识别升级。

### 2. 分支约定

```
main            稳定主线，永远是"能装能用"的状态，只通过合并进入
feat/xxx        新功能（feat/tire-pressure）
fix/xxx         修问题（fix/control-center-offset）
```

一个人开发不需要 develop 分支，改动直接在 `feat/*` 上做，验证后合回 `main`：

```bash
git switch -c feat/tire-pressure     # 开分支
# ... 改代码、构建、手机上验证 ...
git add -A && git commit -m "feat: 新增胎压监测模块"
git switch main && git merge feat/tire-pressure
```

### 3. 提交信息规范

格式：`<类型>: <一句话说明>`（中文即可）

| 类型 | 用于 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `ui` | 纯视觉调整（间距/字号/颜色/图标） |
| `refactor` | 重构，不改行为 |
| `perf` | 性能优化 |
| `chore` | 构建脚本、依赖、文档等杂项 |

示例：`ui: 续航区三行间距收紧`、`fix: 控车按钮未居中`、`chore(release): v0.1.1`

理由：变更记录 CHANGELOG 是**自动从这些信息生成的**，写清楚省事。

### 4. 发版（一条命令搞定）

```bash
cd LeapBuddy
./scripts/release.sh 0.1.1
```

脚本依次做：
1. 检查工作区干净（有未提交改动会先提示你处理）
2. 改 `app/build.gradle.kts` 的 `versionName` / `versionCode`
3. 把自上一个标签以来的提交整理成条目，写进 `CHANGELOG.md`
4. 构建 APK，另存为 `release/零控-v0.1.1-debug.apk`
5. 提交 + 打标签 `v0.1.1`
6. 推送到 GitHub；若本机 git 协议被网络拦截，自动改用 API 通道同步
7. 在 GitHub 上创建 Release 并把 APK 作为附件上传

**先跳过构建**（只想快速更新版本号时用）：`./scripts/release.sh 0.1.1 --no-build`

### 5. 同步到 GitHub

正常情况下用 `git push` 即可。若出现 `CONNECT tunnel failed 502`（本机代理会拦 `github.com` 的 git 协议），改用：

```bash
GH_TOKEN=<你的令牌> python3 scripts/gh-sync.py
```

它会增量同步：只上传发生变化的文件，提交历史连续累加，不会覆盖已有记录。

---

## 目录结构

```
LeapBuddy/
├── app/                     源码主工程
│   └── src/main/java/com/leapbuddy/app/
│       ├── Models.kt            指令表（Commands）与预约计划
│       ├── LeapmotorApi.kt      官方接口实现
│       ├── SessionStore.kt      本地偏好与会话（部分走 Keystore 加密）
│       └── ui/                  Compose 界面层
├── gradle/
├── scripts/                 发版与同步工具
│   ├── release.sh           一键发版
│   ├── gh-sync.py           API 通道增量同步
│   └── gh-release.py        创建 Release 并上传 APK
├── CHANGELOG.md
└── README.md
```

## 安全须知

- 源码含零跑官方服务端的逆向协议实现，**仓库必须保持私有**
- `AmapWebApi` 的高德 Key 由使用者自己在「我的」页面填写，未硬编码
- 登录凭证与控车密码走 Android Keystore 加密存储（`SecureValueStore`）
- 切勿把 `local.properties`、签名文件（`.jks`）、令牌提交进来——已在 `.gitignore` 中屏蔽
