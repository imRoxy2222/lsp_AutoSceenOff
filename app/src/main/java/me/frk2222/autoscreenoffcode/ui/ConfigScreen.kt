package me.frk2222.autoscreenoffcode.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.data.LauncherIcon
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference


private data class DurationPreset(val value: Int, val unit: String, val text: String)

/**
 * 配置页：全局规则 + 应用单独规则，都在这一个页面里，从下往上读就是配置顺序。
 *
 * 单应用的细分规则点进去才展开（「配置」标签内部再压一页），避免首屏被几十个应用刷屏。
 */
@Composable
fun ConfigScreen(
    padding: PaddingValues,
    onOpenApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    val context = LocalContext.current
    val activated = Framework.activated
    val rev = ConfigStore.revision

    val scope = Framework.info.scope
    val apps = remember(scope) { scope.filter { it != "system" } }
    val prefs = remember(rev) { ConfigStore.snapshot() }

    var enabled by remember(rev) { mutableStateOf(ConfigStore.bool(Config.KEY_ENABLED, true)) }
    var debug by remember(rev) { mutableStateOf(ConfigStore.bool(Config.KEY_DEBUG, false)) }
    var unitIndex by remember(rev) {
        mutableStateOf(Config.TimeUnit.indexOfKey(ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)))
    }
    var valueText by remember(rev) {
        mutableStateOf(ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE).toString())
    }

    var hidden by remember { mutableStateOf(LauncherIcon.isHidden(context)) }
    var hiddenHint by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(screenSpacing),
    ) {
        if (!activated) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                Row {
                    StatusDot(color = toneError())
                    Spacer(modifier = Modifier.width(8.dp))
                    Hint(
                        modifier = Modifier.weight(1f),
                        text = "模块未激活，下面的设置写不进去。请先在 LSPosed 里启用本模块，作用域勾选「系统框架」。",
                    )
                }
                }
            }
        }

        // ---------------- 全局开关 ----------------
        item { SmallTitle(text = "全局设置") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        checked = enabled,
                        onCheckedChange = { next ->
                            enabled = next
                            ConfigStore.put(Config.KEY_ENABLED, next)
                        },
                        title = "启用息屏",
                        summary = if (enabled) "按照下方时长计时" else "关闭后所有应用都不再自动息屏",
                        enabled = activated,
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        checked = debug,
                        onCheckedChange = { next ->
                            debug = next
                            ConfigStore.put(Config.KEY_DEBUG, next)
                        },
                        title = "详细日志",
                        summary = "logcat 过滤标签 AutoScreenOff 可看倒计时",
                        enabled = activated,
                    )
                }
            }
        }

        // ---------------- 时长 ----------------
        item { SmallTitle(text = "无操作时长") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = TextFieldValue(valueText),
                        onValueChange = { next ->
                            val filtered = next.text.filter { it.isDigit() }.take(4)
                            valueText = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null && parsed >= 30) {
                                ConfigStore.put(Config.KEY_GLOBAL_VALUE, parsed)
                            } else {
                                ConfigStore.put(Config.KEY_GLOBAL_VALUE, 30) // 最低设置30s, 避免陷入循环
                            }
                        },
                        label = "数值",
                        enabled = activated,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Config.TimeUnit.entries.forEachIndexed { index, unit ->
                            PresetChip(
                                text = unit.label,
                                selected = unitIndex == index,
                                enabled = activated,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    unitIndex = index
                                    ConfigStore.put(Config.KEY_GLOBAL_UNIT, unit.key)
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 应用单独设置 ----------------
        item { SmallTitle(text = "应用单独设置") }

        if (apps.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Hint(
                        if (!Framework.info.scope.contains("system")) {
                            "作用域里还没有「系统框架」，请到 LSPosed 勾选后再来添加应用。"
                        } else {
                            "还没有应用。点下面「添加应用」向 LSPosed 申请，或直接在 LSPosed 里勾选。"
                        },
                    )
                }
            }
        } else {
            items(apps, key = { it }) { pkg ->
                val label = remember(pkg) { appLabel(context, pkg) }
                val summary = remember(rev, pkg) {
                    if (prefs != null) Config.effectiveText(prefs, pkg) else "模块未激活"
                }
                ArrowPreference(
                    title = label,
                    summary = summary,
                    startAction = { AppIcon(pkg = pkg, label = label) },
                    onClick = { onOpenApp(pkg) },
                )
            }
        }

        item {
            ArrowPreference(
                title = "添加应用",
                summary = "向框架申请把更多应用加入作用域",
                onClick = onAddApp,
            )
        }

        // ---------------- 界面 ----------------
        item { SmallTitle(text = "界面") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    checked = hidden,
                    onCheckedChange = { next ->
                        if (LauncherIcon.setHidden(context, next)) {
                            hidden = next
                            hiddenHint = if (next) {
                                "桌面图标已隐藏，以后从 LSPosed → 模块 → 长按本模块 →「启动」打开。" +
                                    "图标要等桌面刷新后才会消失，属正常现象。"
                            } else {
                                "桌面图标已恢复显示。"
                            }
                        } else {
                            hiddenHint = "设置失败，请重试。"
                        }
                    },
                    title = "隐藏桌面图标",
                )
            }
        }

        if (hiddenHint != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Hint(hiddenHint!!)
                }
            }
        }
    }
}

/** 时长预设按钮。选中的那个用主色填充，其余保持灰底，一眼看出当前是哪个 */
@Composable
private fun PresetChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = if (selected) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}
