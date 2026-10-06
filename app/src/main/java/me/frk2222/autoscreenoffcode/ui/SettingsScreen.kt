package me.frk2222.autoscreenoffcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.data.LauncherIcon
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val activated = Framework.activated
    val rev = ConfigStore.revision

    var hidden by remember { mutableStateOf(LauncherIcon.isHidden(context)) }
    var hiddenHint by remember { mutableStateOf<String?>(null) }

    var enabled by remember(rev) { mutableStateOf(ConfigStore.bool(Config.KEY_ENABLED, true)) }
    var systemEnabled by remember(rev) {
        mutableStateOf(ConfigStore.bool(Config.KEY_SYSTEM_ENABLED, Config.DEFAULT_SYSTEM_ENABLED))
    }
    var dryRun by remember(rev) {
        mutableStateOf(ConfigStore.bool(Config.KEY_DRY_RUN, Config.DEFAULT_DRY_RUN))
    }
    var debug by remember(rev) { mutableStateOf(ConfigStore.bool(Config.KEY_DEBUG, false)) }
    var unitIndex by remember(rev) {
        mutableStateOf(Config.TimeUnit.indexOfKey(ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)))
    }
    var valueText by remember(rev) {
        mutableStateOf(ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE).toString())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SwitchPreference(
                checked = systemEnabled,
                onCheckedChange = {
                    systemEnabled = it
                    ConfigStore.put(Config.KEY_SYSTEM_ENABLED, it)
                },
                title = "系统框架息屏（必须打开）",
                summary = if (systemEnabled) {
                    "已启用：system_server 会监听息屏请求。若开机异常，用安全模式停用本模块即可恢复"
                } else {
                    "关闭时不会 hook 系统框架，息屏指令无人执行。默认关闭以保证绝不卡开机"
                },
                enabled = activated,
            )
        }

        item {
            SwitchPreference(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    ConfigStore.put(Config.KEY_ENABLED, it)
                },
                title = "启用息屏",
                summary = "关闭后所有应用都不再自动息屏",
                enabled = activated,
            )
        }

        item {
            SwitchPreference(
                checked = dryRun,
                onCheckedChange = {
                    dryRun = it
                    ConfigStore.put(Config.KEY_DRY_RUN, it)
                },
                title = "安全模式（只记录，不息屏）",
                summary = "建议先用「立即测试息屏」验证 ROM 可用后再关闭",
                enabled = activated,
            )
        }

        item {
            SwitchPreference(
                checked = debug,
                onCheckedChange = {
                    debug = it
                    ConfigStore.put(Config.KEY_DEBUG, it)
                },
                title = "详细日志",
                summary = "在 logcat（标签 AutoScreenOff）中打印倒计时",
                enabled = activated,
            )
        }

        item { SmallTitle(text = "全局默认时长") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("无操作多久后息屏")
                    TextField(
                        value = TextFieldValue(valueText),
                        onValueChange = { next ->
                            val filtered = next.text.filter { it.isDigit() }.take(4)
                            valueText = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                ConfigStore.put(Config.KEY_GLOBAL_VALUE, parsed)
                            }
                        },
                        label = "数值",
                        enabled = activated,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            OverlayDropdownPreference(
                items = Config.TimeUnit.LABELS,
                selectedIndex = unitIndex,
                title = "时间单位",
                enabled = activated,
                onSelectedIndexChange = { index ->
                    unitIndex = index
                    ConfigStore.put(Config.KEY_GLOBAL_UNIT, Config.TimeUnit.entries[index].key)
                },
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("全局默认：${valueText.ifEmpty { "-" }} ${Config.TimeUnit.entries[unitIndex].label}")
                Text("这里设置的是默认值。被勾选的应用默认都用它，也可以在「应用设置」里给单个应用单独设置。")
            }
        }

        item { SmallTitle(text = "界面") }

        item {
            SwitchPreference(
                checked = hidden,
                onCheckedChange = { next ->
                    if (LauncherIcon.setHidden(context, next)) {
                        hidden = next
                        hiddenHint = if (next) {
                            "桌面图标已隐藏。以后这样打开：LSPosed → 模块 → 长按本模块 →「启动」。" +
                                "（图标可能要等桌面刷新或重启桌面后才消失，属正常现象）"
                        } else {
                            "桌面图标已恢复显示。"
                        }
                    } else {
                        hiddenHint = "设置失败，请重试。"
                    }
                },
                title = "隐藏桌面图标",
                summary = if (hidden) {
                    "已隐藏：桌面看不到本 App，从 LSPosed 长按本模块 →「启动」打开"
                } else {
                    "开启后桌面不再显示本 App，只能从 LSPosed 打开"
                },
            )
        }

        if (hiddenHint != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(hiddenHint!!)
                }
            }
        }

        if (hidden) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("万一直进不来怎么办")
                    Text("用电脑执行（把包名换成你自己的）：adb shell pm enable me.frk2222.autoscreenoffcode/.LauncherAlias")
                    Text("或者在 LSPosed 里取消启用本模块再重新启用，图标也会回来。")
                }
            }
        }
    }
}
