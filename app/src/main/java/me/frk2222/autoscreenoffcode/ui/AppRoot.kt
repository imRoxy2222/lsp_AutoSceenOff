package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private enum class Screen { Home, Settings, Apps, AppDetail, AddApp, Help }

@Composable
fun AppRoot() {
    val stack = remember { mutableStateListOf(Screen.Home) }
    var detailPkg by remember { mutableStateOf("") }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val title = when (current) {
        Screen.Home -> "无操作息屏"
        Screen.Settings -> "全局设置"
        Screen.Apps -> "应用设置"
        Screen.AppDetail -> appLabel(LocalContext.current, detailPkg)
        Screen.AddApp -> "添加应用"
        Screen.Help -> "使用说明"
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                            Text("‹")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (current) {
            Screen.Home -> HomeScreen(
                padding = padding,
                onSettings = { stack.add(Screen.Settings) },
                onApps = { stack.add(Screen.Apps) },
                onHelp = { stack.add(Screen.Help) },
            )

            Screen.Settings -> SettingsScreen(padding)
            Screen.Apps -> AppsScreen(
                padding = padding,
                onOpenApp = { pkg -> detailPkg = pkg; stack.add(Screen.AppDetail) },
                onAddApp = { stack.add(Screen.AddApp) },
            )

            Screen.AppDetail -> AppDetailScreen(
                padding = padding,
                pkg = detailPkg,
                onRemoved = { stack.removeAt(stack.lastIndex) },
            )
            Screen.AddApp -> AddAppScreen(padding = padding)

            Screen.Help -> HelpScreen(padding)
        }
    }
}

@Composable
fun HomeScreen(
    padding: PaddingValues,
    onSettings: () -> Unit,
    onApps: () -> Unit,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current
    val activated = Framework.activated
    val info = Framework.info
    val rev = ConfigStore.revision

    val enabled = remember(rev) { ConfigStore.bool(Config.KEY_ENABLED, true) }
    val systemEnabled = remember(rev) {
        ConfigStore.bool(Config.KEY_SYSTEM_ENABLED, Config.DEFAULT_SYSTEM_ENABLED)
    }
    val dryRun = remember(rev) { ConfigStore.bool(Config.KEY_DRY_RUN, Config.DEFAULT_DRY_RUN) }
    val globalText = remember(rev) { ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE) }
    val globalUnit = remember(rev) {
        Config.TimeUnit.fromKey(ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)).label
    }

    var testResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (!activated) {
                    Text("模块未激活")
                    Text("请确认：已在 LSPosed 中启用本模块，并勾选「系统框架」和需要生效的应用，然后重启/强行停止这些应用。")
                    Text("注意：「系统框架息屏」不是 LSPosed 里的一个应用，是本 App 里的开关，就在下面。")
                } else {
                    Text("框架：${info.name} ${info.version}")
                    Text("Xposed API：${info.apiVersion}")
                    Text("息屏能力(system)：${if (info.hasSystemCapability) "支持" else "不支持"}")
                    Text("远程配置(remote)：${if (info.hasRemoteCapability) "支持" else "不支持"}")
                    Text("已生效作用域：${info.scope.size} 个")
                    Text("运行中的目标进程：${info.runningTargets} 个")
                    if (info.scope.isNotEmpty() && !info.scope.contains("system")) {
                        Text("注意：作用域里没有「系统框架」，息屏指令没人执行。")
                    }
                }
            }
        }

        item {
            SwitchPreference(
                checked = systemEnabled,
                onCheckedChange = {
                    if (ConfigStore.put(Config.KEY_SYSTEM_ENABLED, it)) {
                        testResult = if (it) "已开启，system_server 会在 30 秒内完成注册，稍后点下面的按钮测试" else "已关闭"
                    } else {
                        testResult = "写入失败：模块未激活，请先在 LSPosed 里启用本模块并勾选「系统框架」"
                    }
                },
                title = "① 启用「系统框架息屏」",
                summary = if (systemEnabled) {
                    "已启用：system_server 正在监听息屏请求"
                } else {
                    "必须打开。这不是 LSPosed 里的应用，是本 App 的开关。默认关闭以保证绝不卡开机"
                },
                enabled = activated,
            )
        }

        if (activated && !systemEnabled) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("上面的开关是关闭的")
                    Text("只要它关着，system_server 就不会注册息屏接收器，所有息屏请求都无人执行 —— 这也是新装版本绝不卡开机的原因。")
                    Text("打开后无需重启，最多 30 秒自动生效。")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("② 验证息屏是否可用（会立刻关屏，不受安全模式限制）")
                    Button(
                        onClick = { testScreenOff(context) { testResult = it } },
                        enabled = activated,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即测试息屏")
                    }
                    if (testResult != null) {
                        Text(testResult!!)
                    }
                }
            }
        }

        if (activated && dryRun) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("③ 安全模式还开着：只写日志，不会真息屏")
                        Text("日志里出现「本应息屏（未执行）」就是这个原因。上面测试能关屏的话，点这里关掉它，功能就正式生效了。")
                        Button(
                            onClick = {
                                testResult = if (ConfigStore.put(Config.KEY_DRY_RUN, false)) {
                                    "安全模式已关闭，功能正式生效（无需重启 App）"
                                } else {
                                    "写入失败：模块未激活，请先在 LSPosed 里启用本模块"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("关闭安全模式，正式启用")
                        }
                    }
                }
            }
        }

        item {
            ArrowPreference(
                title = "全局默认时长",
                summary = "$globalText $globalUnit" + if (enabled) "" else "（总开关已关闭）",
                onClick = onSettings,
            )
        }

        item {
            ArrowPreference(
                title = "应用单独设置",
                summary = "为勾选的应用分别设置时长",
                onClick = onApps,
            )
        }

        item { ArrowPreference(title = "使用说明", onClick = onHelp) }
    }
}

@Composable
fun HelpScreen(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("怎么用")
                Text("1. 在 LSPosed 里启用本模块。")
                Text("2. 作用域里务必勾选「系统框架」，再勾选抖音 / 快手 / 红果短剧等要生效的应用。")
                Text("3. 重启或强行停止这些应用，让模块注入进去。")
                Text("4. 回到本 App 设置时长：可以只设全局默认，也可以给某个应用单独设。")
                Text("5. 先点「立即测试息屏」确认能关屏，再关掉安全模式。")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("为什么必须勾选系统框架")
                Text("普通 App 没有息屏权限。视频类 App 是靠 WakeLock / 屏幕常亮标志保持亮屏的，改系统休眠时间对它们无效，必须由系统进程强制执行息屏。")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("判定规则")
                Text("只要 App 在前台，且超过设定时长没有任何触摸或按键操作，就息屏。")
                Text("不在乎是否在播放视频、是否有声音。回到前台、亮屏都会重新计时。")
                Text("App 退到后台时不计时，不会干扰你用别的应用。")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("排查")
                Text("如果没生效：确认「系统框架」已勾选、「系统框架息屏」开关已打开、目标 App 已重启、作用域里有它们、安全模式已关闭。")
                Text("日志可通过 logcat 过滤标签 AutoScreenOff 查看，打开「详细日志」能看到倒计时。")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("万一开机卡住（无限重启）")
                Text("1. 开机时连续按音量键，触发 Magisk / KernelSU / APatch 的安全模式，会停用所有模块，进去后到 LSPosed 里取消勾选本模块。")
                Text("2. 或进 TWRP / OrangeFox 等 Recovery，删掉 /data/adb/lspd/config 目录，这会停用全部 Xposed 模块。")
                Text("3. 或删掉 /data/adb/modules 下的 lsposed 模块目录，或刷官方卸载包。")
                Text("4. 有 root shell 时也可执行：setprop persist.sys.autoscreenoff.disable 1（本模块会自行跳过注册）。")
                Text("恢复后请到本 App 关掉「系统框架息屏」，并把 logcat 里的报错发给我。")
            }
        }
    }
}

/**
 * 点一次就走完「开开关 → 发广播 → 看屏幕到底灭没灭」。
 * 2.5 秒后自检屏幕状态：没灭就把原因说清楚，省得用户去翻 logcat。
 */
private fun testScreenOff(context: Context, onResult: (String) -> Unit) {
    if (!ConfigStore.bool(Config.KEY_SYSTEM_ENABLED, Config.DEFAULT_SYSTEM_ENABLED)) {
        val ok = ConfigStore.put(Config.KEY_SYSTEM_ENABLED, true)
        onResult(
            if (ok) "已替你打开「系统框架息屏」。system_server 会在 30 秒内完成注册，请等一会儿再点一次本按钮。"
            else "写入配置失败：模块未激活。请先在 LSPosed 里启用本模块并勾选「系统框架」，再重启。"
        )
        return
    }

    val token = ConfigStore.ensureToken()
    val intent = Intent(Config.ACTION_SCREEN_OFF)
    intent.putExtra(Config.EXTRA_PACKAGE, "ui.test")
    intent.putExtra(Config.EXTRA_TOKEN, token)
    intent.putExtra(Config.EXTRA_FORCE, true)
    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

    runCatching { context.sendBroadcast(intent) }
        .onFailure {
            onResult("发送广播失败：${it.message}")
            return
        }

    onResult("已发送，2.5 秒后自检…")

    Handler(Looper.getMainLooper()).postDelayed({
        val pm = runCatching {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }.getOrNull()
        val stillOn = pm == null || runCatching { pm.isInteractive }.getOrDefault(true)
        if (stillOn) {
            onResult(
                "屏幕没反应 —— system_server 侧没有执行息屏。按顺序检查：" +
                    "① LSPosed 作用域里勾选了「系统框架」；② 勾选后重启过手机；" +
                    "③ 上面的「启用系统框架息屏」是打开的且已等满 30 秒；" +
                    "④ 日志：adb logcat -s AutoScreenOff"
            )
        }
    }, 2500)
}
