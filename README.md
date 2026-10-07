# autoScreenOff

一个基于 **libxposed API 102** 的 LSPosed 模块：为被勾选的应用按「无操作时长」强制息屏，用于解决老人刷抖音 / 快手 / 红果短剧时睡着导致手机整夜播放的问题。

- 不关心是否在播放视频，只看**有没有触摸操作**；
- 支持**全局默认时长** + **单应用覆盖**；
- 时长 = 数值 + 单位（秒 / 分 / 时），可动态修改；
- UI 使用 MIUIX 风格（纯因为好看，非小米专属）。

---

## 二、工作原理

```
┌──────────────── 目标 App 进程（抖音 / 快手 / 红果短剧）────────────────┐
│  hook Activity.dispatchTouchEvent / dispatchKeyEvent  →  记录最后操作时间 │
│  hook Activity.onResume / onPause                    →  判断前台 / 重新计时│
│  每 5 秒检查一次：前台 && 无操作时长 ≥ 设定值  →  发送广播                │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ 广播 ACTION_SCREEN_OFF（带令牌）
┌──────────────────────────────▼──────────────────────────────────────┐
│  system_server 进程                                                   │
│  动态注册广播接收器 → 反射调用 PowerManager.goToSleep(@hide)          │
└──────────────────────────────────────────────────────────────────────┘
```

为什么必须分两段：普通 App 没有 `DEVICE_POWER` 权限，调不了 `goToSleep`；而视频类 App 是靠 WakeLock / 屏幕常亮标志保持亮屏的，**改系统休眠时间对它们无效**，必须由系统进程强制执行。

为什么不用管「是否在播放视频」：判定只看**有没有触摸或按键操作**，不在乎播放状态，这正是需求。

跨进程配置：UI 写入 libxposed 的 remote preferences，框架负责同步到各个被 hook 的进程，所以**改完设置立刻生效，不需要重启应用**。名字里的 remote 指的是**跨进程**，数据始终在本机，模块不联网、不上传任何内容。

---

## 三、使用步骤

1. 在 LSPosed 里启用本模块。
2. 作用域里**务必勾选「系统框架」**，再勾选抖音 / 快手 / 红果短剧等要生效的应用。
3. 重启（或强行停止）这些应用，让模块注入进去。
4. 打开本 App，**在首页先打开「① 启用系统框架息屏」**（默认关闭，见下方安全设计）。
   注意：这不是 LSPosed 里的一个应用，是本 App 里的开关。打开后 system_server 会在 30 秒内完成注册，不需要重启。
5. 点首页的「立即测试息屏」—— 它会自己检查屏幕有没有真的灭掉，没灭会直接告诉你该查什么。
6. 再设置时长：
   - **全局默认时长**：「配置」标签，数值 + 单位（秒 / 分钟 / 小时），默认 30 分钟，也有预设值可一键点选。
   - **应用单独设置**：「配置」标签下半部分，每个已勾选的应用可单独设为「跟随全局 / 单独设置 / 该应用不生效」。
   - **息屏前提醒**：同上，默认开启。距息屏只剩约 10 秒（两次检测）时，在被托管的应用上弹一条带倒计时的提示条，并**一直显示**到用户动一下屏幕（取消本次息屏）或真的息屏为止。
7. 确认息屏可用后，回**首页关掉「② 安全模式」**，功能才真正生效。

### 界面结构

底部三个标签，切标签各自保留自己的层级（跟微信一样，返回键只弹当前标签）：

| 标签     | 内容                                                                                                                                                                                    |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **首页** | 顶部状态卡：一句话结论 + 引导，下面列出框架 / Xposed API / 息屏能力 / 跨进程配置 / 已生效作用域；中部是全局时长概览与「① 启用系统框架息屏」「② 安全模式」两个必点开关和「立即测试息屏」 |
| **配置** | 全局设置（总开关、详细日志、息屏前提醒）→ 无操作时长（数值 + 单位 + 四个预设）→ 应用单独设置（列表 + 添加应用）→ 界面（隐藏桌面图标）                                                     |
| **关于** | 应用信息与版本、GitHub 项目地址、开机异常时的紧急自救方法                                                                                                                               |

> 「关于」页的仓库地址写在 `ui/AboutScreen.kt` 顶部的 `GITHUB_URL` 常量里，只改那一处即可。

### 安全设计

hook `system_server` 的风险在于：里面**任何一个线程抛出未捕获异常，`RuntimeInit` 的默认处理器都会 `System.exit()`，整机立刻重启**。所以这里有五条铁律：

1. **绝不调用 `ActivityThread.systemMain()`**。它会 new 一个 ActivityThread 并覆盖 `sCurrentActivityThread`，把系统自己那个架空 —— 这是卡开机的头号凶手。拿不到 ActivityThread 就返回 false 等下一轮重试。
2. **先等开机完成**（轮询 `sys.boot_completed` / `dev.bootcomplete`，最多 5 分钟），再缓冲 20 秒，然后才做任何事。开机阶段的 AMS / PMS 没就绪，抢跑必死或撞 Watchdog。
3. **我们自己开的每个线程都装 `UncaughtExceptionHandler`**，并且线程体再包一层 try/catch。
4. **`onReceive` 跑在 system_server 主线程，绝不做 IPC**。令牌等跨进程数据一律后台预热、缓存到 volatile 字段；真正的息屏丢到工作线程。
5. **默认不注册**。`system_enabled` 默认 `false`，新装版本一定不会卡开机，用户明确打开开关后才 hook 系统框架。

另外：`goToSleep` 在历代 Android 上签名不同（1 参数 / 3 参数），按参数个数探测；广播带令牌校验；息屏有最小 3 秒间隔限流。

### 万一开机卡住（无限重启）

1. **开机时连续按音量键**，触发 Magisk / KernelSU / APatch 的安全模式，会停用所有模块；进去后到 LSPosed 取消勾选本模块。
2. 或进 TWRP / OrangeFox 等 Recovery，**删掉 `/data/adb/lspd/config`** 目录 —— 这会停用全部 Xposed 模块。
3. 或删掉 `/data/adb/modules` 下的 lsposed 模块目录，或刷官方卸载包。
4. 有 root shell 时：`setprop persist.sys.autoscreenoff.disable 1`（模块会自行跳过注册）。

恢复后请关掉「系统框架息屏」，并把 `adb logcat -s AutoScreenOff` 的报错发出来。

### 隐藏桌面图标

在「配置 → 界面 → 隐藏桌面图标」里开关。

**原理**：桌面入口不是 `MainActivity` 本身，而是 manifest 里的 `activity-alias`（`.LauncherAlias`）。把它置为 `DISABLED`，桌面就查不到带 `LAUNCHER` 的组件，图标消失。

**隐藏后怎么打开**：`MainActivity` 声明了 Xposed 生态惯例的 category `de.robv.android.xposed.category.MODULE_SETTINGS`。LSPosed 的「启动」菜单**优先**查这个 category 并用 `setClassName` 显式启动（`AppHelper.getSettingsIntent`），跟有没有桌面图标无关。所以：

> LSPosed → 模块 → **长按**本模块 → 「启动」

### 在 App 内申请 / 移除作用域

不必每次都去 LSPosed 里勾。「配置 → 应用单独设置 → 添加应用」里勾选后点「添加 N 个应用到作用域」，会调用 `XposedService.requestScope()`，LSPosed 弹出确认框，同意后自动勾进本模块作用域。反向操作在应用详情页的「从作用域移除」，调用 `removeScope()`。

要点：

- `scope.list` 只是**推荐列表**，决定 LSPosed 里默认展示哪些应用；`module.prop` 里 `staticScope=false`，所以任何应用都可以被勾选，不限于这个列表。
- **「添加应用」列出的是手机上全部已安装应用**（含没有桌面图标的系统组件），不是只列 `scope.list` 里的几个。
- Android 11（API 30）起有包可见性限制，manifest 里必须声明 `QUERY_ALL_PACKAGES` + `<queries>`（MAIN/LAUNCHER），否则 `getInstalledApplications` / `queryIntentActivities` 只会返回寥寥几个。这是本项目最容易踩的坑之一。
- 申请成功后，目标 App 必须**强行停止或重启**才会被注入生效。
- 申请结果通过 `OnScopeEventListener` 回调，界面上直接显示批准了哪几个。

### 排查

日志用 logcat 过滤标签 `AutoScreenOff` 查看。打开「详细日志」后，每 5 秒会打印一次 `已 Xs / Ys` 的倒计时。

不生效时按顺序检查：模块是否启用 → 作用域是否含「系统框架」 → 目标 App 是否已重启 → 安全模式是否已关闭。

---

## 一、版本清单

### 工具链

| 组件                            | 版本                       | 是否需要你安装 | 说明                                                                   |
| ------------------------------- | -------------------------- | -------------- | ---------------------------------------------------------------------- |
| JDK                             | **17**（本机 17.0.2）      | ✅ 必须        | AGP 9 与 Gradle 9 都要求 JDK 17。**不要用 JDK 25**，Gradle 会直接报错  |
| Gradle                          | **9.5.0**                  | ❌ 自动        | 由 `gradle/wrapper/gradle-wrapper.properties` 指定，首次运行时自动下载 |
| Android Gradle Plugin (AGP)     | **9.3.3**                  | ❌ Maven 拉取  | 版本写在 `gradle/libs.versions.toml` 的 `agp`                          |
| Kotlin（含 Compose 编译器插件） | **2.4.20**                 | ❌ Maven 拉取  | 同上，`kotlin` 项；Compose 编译器版本必须与 Kotlin 版本一致            |
| Android SDK Platform            | **API 37**（android-37.0） | ✅ 必须        | `compileSdk`                                                           |
| Android SDK Build-Tools         | **36.0.0**                 | ✅ 必须        | 打包 / zipalign / apksigner 都在这里                                   |
| Android SDK Platform-Tools      | 最新                       | ✅ 必须        | 提供 `adb`                                                             |
| Android Studio                  | 最新稳定版                 | 推荐           | 不是编译必需项，但管理 SDK、看 logcat、调 Compose 预览很方便           |

### 编译参数（`app/build.gradle.kts`）

| 参数                          | 值        | 含义                                              |
| ----------------------------- | --------- | ------------------------------------------------- |
| `compileSdk`                  | **37**    | 用哪一版 Android SDK 编译。**不能降**，原因见下节 |
| `targetSdk`                   | **36**    | 声明适配到的最高版本，影响系统行为策略            |
| `minSdk`                      | **26**    | 最低可安装版本，即 Android 8.0                    |
| `versionCode` / `versionName` | 1 / "1.0" | 发版时在这里改                                    |
| Java 兼容级别                 | **17**    | `sourceCompatibility` / `targetCompatibility`     |

> ⚠️ 虽然 `minSdk = 26`（8.0），但 **LSPosed 官方只支持 Android 8.1（API 27）及以上**，所以实际可用范围是从 8.1 起。

### 依赖库（`gradle/libs.versions.toml`）

| 库                                               | 版本                         | 作用                                        |
| ------------------------------------------------ | ---------------------------- | ------------------------------------------- |
| Compose BOM                                      | `2026.08.00`（Compose 1.12） | 统一管理 Compose 各子库版本                 |
| `androidx.core:core-ktx`                         | 1.19.1                       | Android KTX 扩展                            |
| `androidx.lifecycle:lifecycle-runtime-ktx`       | 2.11.0                       | 生命周期                                    |
| `androidx.activity:activity-compose`             | 1.13.0                       | Compose 与 Activity 桥接                    |
| `top.yukonga.miuix.kmp:miuix-ui-android`         | **0.9.4**                    | MIUIX 基础组件                              |
| `top.yukonga.miuix.kmp:miuix-preference-android` | **0.9.4**                    | MIUIX 设置项组件                            |
| `top.yukonga.miuix.kmp:miuix-icons-android`      | **0.9.4**                    | MIUIX 图标                                  |
| `io.github.libxposed:api`                        | **102.0.0**                  | Xposed API，`compileOnly`（框架运行时提供） |
| `io.github.libxposed:service`                    | **102.0.0**                  | 模块 UI 侧向 hook 进程写 remote prefs       |

---

## 二、为什么必须是这一套版本（别手贱降级）

这是本项目最反直觉的一条约束，改动版本前务必先看：

```
MIUIX 0.9.x 的 AAR 元数据里 minCompileSdk = 37
        ↓
compileSdk 必须 ≥ 37
        ↓
AGP 必须 9.x（AGP 8 最高只支持到 API 36）
        ↓
Gradle 必须 9.x（AGP 9 要求的 Gradle 版本）
```

MIUIX 从 0.9.0 到 0.9.4 **全部**要求 `minCompileSdk=37`，所以「降 MIUIX 版本来适配 Gradle 8」这条路是死的。

## 三、环境准备（只做一次）

1. **JDK 17**：确保 `java -version` 输出 17.x。Android Studio 里确认 `Settings → Build Tools → Gradle → Gradle JDK` 选的是 17。
2. **Android SDK**：AS 的 SDK Manager（`Tools → SDK Manager`）里勾选：
   - `Android API 37` platform
   - `Android SDK Build-Tools 36`
   - `Android SDK Platform-Tools`
3. **`local.properties`**：AS 会自动生成，内容是 SDK 路径。这个文件**不要提交到 Git**（已在 `.gitignore` 中）。
4. **代理（仅本机）**：Gradle / Java 不读系统环境变量里的代理，必须写进 `C:\Users\<你>\.gradle\gradle.properties`。该文件是**用户级**的，不属于项目代码，不会影响开源。

---

## 四、编译命令大全

> 所有命令都在 **`code/` 目录**下执行（也就是 `gradlew.bat` 所在的那一层）。
>
> Windows 上用 `gradlew.bat`，Git Bash / PowerShell 均可；macOS / Linux 用 `./gradlew`。

### 4.1 最常用的几条

| 目的                          | 命令                               |
| ----------------------------- | ---------------------------------- |
| 打 Debug 包（开发自测用）     | `gradlew.bat :app:assembleDebug`   |
| 打 Release 包（发布用）       | `gradlew.bat :app:assembleRelease` |
| 打 Debug 包并直接装到手机     | `gradlew.bat :app:installDebug`    |
| 打 Release 包并直接装到手机   | `gradlew.bat :app:installRelease`  |
| 打 AAB（上架 Google Play 用） | `gradlew.bat :app:bundleRelease`   |
| 清理构建产物                  | `gradlew.bat clean`                |
| 停掉后台 Gradle 守护进程      | `gradlew.bat --stop`               |

装到手机前请确认 `adb devices` 能看到你的设备，且已开启 USB 调试。

### 4.2 手动安装（不通过 Gradle）

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` 表示覆盖安装（保留应用数据）。

### 4.3 测试与检查

| 目的                         | 命令                                                                    |
| ---------------------------- | ----------------------------------------------------------------------- |
| 跑本地单元测试               | `gradlew.bat :app:test`                                                 |
| 跑真机仪器化测试             | `gradlew.bat :app:connectedAndroidTest`                                 |
| 代码静态检查（Lint）         | `gradlew.bat :app:lintDebug`                                            |
| 查看依赖树                   | `gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath` |
| 查看所有可用任务             | `gradlew.bat tasks`                                                     |
| 只编译不打包（快速查语法错） | `gradlew.bat :app:compileDebugKotlin`                                   |

### 4.4 产物位置

| 构建类型              | 输出路径                                                 |
| --------------------- | -------------------------------------------------------- |
| Debug APK             | `app/build/outputs/apk/debug/app-debug.apk`              |
| Release APK（已签名） | `app/build/outputs/apk/release/app-release.apk`          |
| Release APK（未签名） | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Release AAB           | `app/build/outputs/bundle/release/app-release.aab`       |

---

## 五、签名（发版前必看）

Android 要求每个 APK 都有签名才能安装。**Debug 包**由 Android Studio 自动用调试密钥签名，直接可装；**Release 包**默认没有签名配置，产物是 `app-release-unsigned.apk`，装不上。

### 方式 A：配置 `keystore.properties`（推荐，一劳永逸）

第一步，生成密钥库（只需做一次，生成的 `.jks` 文件请妥善备份，丢了就无法升级覆盖安装）：

```bash
keytool -genkeypair -v ^
  -keystore autoscreenoff.jks ^
  -keyalg RSA -keysize 2048 -validity 10000 ^
  -alias autoscreenoff
```

第二步，在项目根目录（`code/`）新建 `keystore.properties`：

```properties
storeFile=autoscreenoff.jks
storePassword=你的密钥库密码
keyAlias=autoscreenoff
keyPassword=你的密钥密码
```

第三步，直接构建：

```bash
gradlew.bat :app:assembleRelease
```

此时产物是已签名的 `app-release.apk`。**没有** `keystore.properties` 时行为不变（仍产出 unsigned 包），所以不配置也不会让构建失败。

> `keystore.properties`、`*.jks`、`*.keystore` 已加入 `.gitignore`，不会被提交。

## 六、代码结构

```
app/src/main/
├── resources/META-INF/xposed/
│   ├── java_init.list     # 模块入口类名
│   ├── scope.list         # 推荐作用域（含 system）
│   └── module.prop        # minApiVersion=102
└── java/me/frk2222/autoscreenoffcode/
    ├── MainActivity.kt
    ├── xposed/
    │   ├── HookEntry.kt         # 入口：区分 system_server / 普通 App
    │   ├── AppMonitor.kt        # 目标 App 侧：记录操作、计时、发广播
    │   ├── ScreenOffService.kt  # system_server 侧：收广播、反射 goToSleep
    │   ├── WarnToast.kt         # 息屏前提示条：常悬浮窗 + 倒计时，触屏/息屏才收
    │   └── Config.kt            # 配置键与解析（三处共用）
    ├── data/
    │   ├── Framework.kt         # 框架服务绑定与能力位
    │   ├── ConfigStore.kt       # UI 侧读写跨进程配置（remote prefs）
    │   └── LauncherIcon.kt      # 桌面图标显示 / 隐藏
    └── ui/
        ├── theme/Theme.kt       # 自定义配色（页面底 + 卡片 + 强调色）
        ├── UiKit.kt             # 语义色、状态点、信息行等共用小件
        ├── AppRoot.kt           # 底部三标签外壳，每标签独立返回栈
        ├── HomeScreen.kt        # 首页：状态卡 + 快捷操作 + 息屏自检
        ├── ConfigScreen.kt      # 配置：全局规则 + 应用列表
        ├── AppDetailScreen.kt   # 单个应用的单独设置
        ├── AddAppScreen.kt      # 向 LSPosed 申请扩充作用域
        ├── AboutScreen.kt       # 关于：版本 / GitHub / 紧急自救
        ├── ScopeApi.kt          # requestScope / removeScope 封装
        └── AppIcon.kt           # 应用图标异步加载 + LruCache
```

