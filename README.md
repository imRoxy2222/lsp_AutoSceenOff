# autoScreenOff

一个基于 **libxposed API 102** 的 LSPosed 模块：为被勾选的应用按「无操作时长」强制息屏，用于解决老人刷抖音 / 快手 / 红果短剧时睡着导致手机整夜播放的问题。

- 不关心是否在播放视频，只看**有没有触摸操作**；
- 支持**全局默认时长** + **单应用覆盖**；
- 时长 = 数值 + 单位（秒 / 分 / 时），可动态修改；
- UI 使用 MIUIX 风格（纯因为好看，非小米专属）。

> 当前进度：工具链已调通，Debug / Release 均可编译。功能代码（Xposed 骨架、检测链路、息屏执行）尚未开始。

---

## 一、版本清单

### 工具链

| 组件 | 版本 | 是否需要你安装 | 说明 |
|---|---|---|---|
| JDK | **17**（本机 17.0.2） | ✅ 必须 | AGP 9 与 Gradle 9 都要求 JDK 17。**不要用 JDK 25**，Gradle 会直接报错 |
| Gradle | **9.5.0** | ❌ 自动 | 由 `gradle/wrapper/gradle-wrapper.properties` 指定，首次运行时自动下载 |
| Android Gradle Plugin (AGP) | **9.3.3** | ❌ Maven 拉取 | 版本写在 `gradle/libs.versions.toml` 的 `agp` |
| Kotlin（含 Compose 编译器插件） | **2.4.20** | ❌ Maven 拉取 | 同上，`kotlin` 项；Compose 编译器版本必须与 Kotlin 版本一致 |
| Android SDK Platform | **API 37**（android-37.0） | ✅ 必须 | `compileSdk` |
| Android SDK Build-Tools | **36.0.0** | ✅ 必须 | 打包 / zipalign / apksigner 都在这里 |
| Android SDK Platform-Tools | 最新 | ✅ 必须 | 提供 `adb` |
| Android Studio | 最新稳定版 | 推荐 | 不是编译必需项，但管理 SDK、看 logcat、调 Compose 预览很方便 |

### 编译参数（`app/build.gradle.kts`）

| 参数 | 值 | 含义 |
|---|---|---|
| `compileSdk` | **37** | 用哪一版 Android SDK 编译。**不能降**，原因见下节 |
| `targetSdk` | **36** | 声明适配到的最高版本，影响系统行为策略 |
| `minSdk` | **26** | 最低可安装版本，即 Android 8.0 |
| `versionCode` / `versionName` | 1 / "1.0" | 发版时在这里改 |
| Java 兼容级别 | **17** | `sourceCompatibility` / `targetCompatibility` |

> ⚠️ 虽然 `minSdk = 26`（8.0），但 **LSPosed 官方只支持 Android 8.1（API 27）及以上**，所以实际可用范围是从 8.1 起。

### 依赖库（`gradle/libs.versions.toml`）

| 库 | 版本 | 作用 |
|---|---|---|
| Compose BOM | `2026.08.00`（Compose 1.12） | 统一管理 Compose 各子库版本 |
| `androidx.core:core-ktx` | 1.19.1 | Android KTX 扩展 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | 生命周期 |
| `androidx.activity:activity-compose` | 1.13.0 | Compose 与 Activity 桥接 |
| `top.yukonga.miuix.kmp:miuix-ui-android` | **0.9.4** | MIUIX 基础组件 |
| `top.yukonga.miuix.kmp:miuix-preference-android` | **0.9.4** | MIUIX 设置项组件 |
| `top.yukonga.miuix.kmp:miuix-icons-android` | **0.9.4** | MIUIX 图标 |
| `io.github.libxposed:api` | **102.0.0** | Xposed API，`compileOnly`（框架运行时提供） |
| `io.github.libxposed:service` | **102.0.0** | 模块 UI 侧向 hook 进程写 remote prefs |

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

### 关于 vfox 里的 kotlin / gradle 版本

**它们是无效的**，不会参与本项目的构建：

- Gradle 用的是 `gradle-wrapper.properties` 里指定的发行包，**不是** `vfox use gradle` 的那个；
- Kotlin 编译器是 Gradle 从 Maven 拉的 `kotlin-gradle-plugin`，版本由 `libs.versions.toml` 决定，**不是** `vfox use kotlin` 的那个。

所以你之前用 vfox 装的 Kotlin 2.3.20 / Gradle 8.11.1 都**没有**被这个项目用上（实际生效的是 2.4.20 / 9.5.0）。唯一真正需要本机提供的是 **JDK 17**。

---

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

| 目的 | 命令 |
|---|---|
| 打 Debug 包（开发自测用） | `gradlew.bat :app:assembleDebug` |
| 打 Release 包（发布用） | `gradlew.bat :app:assembleRelease` |
| 打 Debug 包并直接装到手机 | `gradlew.bat :app:installDebug` |
| 打 Release 包并直接装到手机 | `gradlew.bat :app:installRelease` |
| 打 AAB（上架 Google Play 用） | `gradlew.bat :app:bundleRelease` |
| 清理构建产物 | `gradlew.bat clean` |
| 停掉后台 Gradle 守护进程 | `gradlew.bat --stop` |

装到手机前请确认 `adb devices` 能看到你的设备，且已开启 USB 调试。

### 4.2 手动安装（不通过 Gradle）

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` 表示覆盖安装（保留应用数据）。

### 4.3 测试与检查

| 目的 | 命令 |
|---|---|
| 跑本地单元测试 | `gradlew.bat :app:test` |
| 跑真机仪器化测试 | `gradlew.bat :app:connectedAndroidTest` |
| 代码静态检查（Lint） | `gradlew.bat :app:lintDebug` |
| 查看依赖树 | `gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath` |
| 查看所有可用任务 | `gradlew.bat tasks` |
| 只编译不打包（快速查语法错） | `gradlew.bat :app:compileDebugKotlin` |

### 4.4 常用附加参数

| 参数 | 作用 |
|---|---|
| `--console=plain` | 输出不带进度动画，方便重定向到日志文件 |
| `--info` / `--debug` | 输出更详细日志，排查依赖问题时用 |
| `--stacktrace` | 报错时打印堆栈 |
| `--offline` | 离线构建，只用本地缓存（依赖已下载完时可加速） |
| `--refresh-dependencies` | 强制重新解析依赖（怀疑缓存损坏时用） |
| `--no-daemon` | 不用守护进程，内存紧张时用 |

例子：

```bash
gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

### 4.5 用 Android Studio 图形界面

不想敲命令的话：

- `Build → Make Project`（只编译）
- `Build → Build Bundle(s) / APK(s) → Build APK(s)`（打包，等价于 assembleDebug）
- `Build → Generate Signed Bundle / APK...`（带签名打包，推荐发布时用这个）
- 三角形 **Run** 按钮 = 编译 + 安装 + 启动

### 4.6 产物位置

| 构建类型 | 输出路径 |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK（已签名） | `app/build/outputs/apk/release/app-release.apk` |
| Release APK（未签名） | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` |

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

### 方式 B：用 apksigner 手动签名

先做 zipalign 对齐，再签名（顺序不能反）：

```bash
set BT=C:\Users\<你>\AppData\Local\Android\Sdk\build-tools\36.0.0

%BT%\zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk

%BT%\apksigner sign --ks autoscreenoff.jks ^
  --ks-key-alias autoscreenoff ^
  --out app-release.apk app-release-aligned.apk
```

验证签名：

```bash
%BT%\apksigner verify -v app-release.apk
```

---

## 六、目录结构

```
code/
├── app/
│   ├── build.gradle.kts          # 模块构建配置：SDK 版本、依赖、签名
│   ├── proguard-rules.pro        # 混淆规则（含 libxposed 官方规则）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/me/frk2222/autoscreenoffcode/
│       │   └── MainActivity.kt
│       └── res/                  # 图标、主题、字符串
├── gradle/
│   ├── libs.versions.toml        # ★ 所有版本号集中在这里
│   └── wrapper/gradle-wrapper.properties   # ★ Gradle 发行版版本
├── gradle.properties             # AndroidX 开关、配置缓存等
├── settings.gradle.kts           # 仓库地址、模块声明
└── gradlew.bat / gradlew         # Gradle 启动器（不要手改）
```

**改版本只动两个文件**：`gradle/libs.versions.toml` 和 `gradle/wrapper/gradle-wrapper.properties`。

---

## 七、常见报错与处理

| 报错关键信息 | 原因 | 处理 |
|---|---|---|
| `Remote host terminated the handshake` / `Could not download ...` | 代理或网络抖动，依赖没下下来 | 先直接重试一次；反复失败就在 `~/.gradle/gradle.properties` 配好代理，或加 `--refresh-dependencies` |
| `Requires compileSdk 37` / `minCompileSdk=37` | SDK Platform 37 没装 | SDK Manager 装 API 37 |
| `Gradle JVM ... incompatible` / JDK 版本相关 | 用了非 JDK 17 | 切回 JDK 17 |
| `useAndroidX` 相关报错 | `gradle.properties` 里缺 `android.useAndroidX=true` | 该文件已配置好，别删 |
| 首次构建卡在 `Downloading gradle-9.5.0-bin.zip` | 网络问题导致发行包下不动 | 用浏览器 / 下载工具取回 zip，或换个网络环境重试 |
| 改了版本号但构建没变化 | 配置缓存命中 | 加 `--rerun-tasks`，或删掉 `.gradle` 与 `app/build` 后重建 |

---

## 八、开发路线（后续）

1. ✅ 工具链调通，Debug / Release 编译通过
2. ⬜ Xposed 骨架：`META-INF/xposed/{java_init.list, scope.list, module.prop}` + `android:label` / `android:description`，只打日志，验证能同时注入「系统框架」与目标 App
3. ⬜ 无操作检测：hook `Activity.dispatchTouchEvent` 记录最后触摸 + 生命周期判前台 + 定时器
4. ⬜ 息屏执行：system_server 侧反射 `PowerManager.goToSleep`（多签名按 SDK 分支探测），默认关闭、只打日志
5. ⬜ 配置通道与 UI：全局默认 + 单应用覆盖，时长数值 + 单位
6. ⬜ 自检页：显示框架名称/版本、API 版本、`PROP_CAP_SYSTEM`、当前 ROM 息屏是否可用
7. ⬜ 真机验证后打开真正的息屏开关

### 安全约定

因为要在 `system_server` 里执行代码，一旦出错会导致系统反复重启，所以：

- 所有 hook 代码必须 try/catch 全覆盖；
- 必须检查 `PROP_CAP_SYSTEM` 能力位；
- 首次启用默认「只记日志、不执行息屏」，真机验证通过后再打开。
