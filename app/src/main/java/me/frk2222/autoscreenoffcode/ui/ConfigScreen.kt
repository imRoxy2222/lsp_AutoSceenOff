package me.frk2222.autoscreenoffcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.data.LauncherIcon
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 应用行：卡片 16dp + 图标 36dp + 图标与文字间隔 8dp = 文字从 60dp 开始，分割线跟它对齐
 */
private val appRowDivider = PaddingValues(start = 60.dp, end = 16.dp)

/**
 * 配置页：全局规则 + 应用单独规则，都在这一个页面里，从上往下读就是配置顺序。
 *
 * 单应用的细分规则点进去才展开（「配置」标签内部再压一页），避免首屏被几十个应用刷屏。
 *
 * 卡片内边距规则和首页一致：装普通内容的 Card 传 [cardPadding]，装 preference 的 Card 不传，
 * 中间的分割线自己用 [dividerPadding] 缩进。
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
    var iconNotice by remember { mutableStateOf<Notice?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(screenSpacing),
    ) {
        // ---------------- 未激活：整页第一眼就要看见 ----------------
        if (!activated) {
            item {
                NoticeCard(
                    notice = Notice(
                        "模块未激活，下面的设置写不进去。请先在 LSPosed 里启用本模块，作用域勾选「系统框架」。",
                        NoticeTone.ERROR,
                    ),
                )
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
                    HorizontalDivider(modifier = Modifier.padding(dividerPadding))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = cardPadding,
            ) {
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
                            ChipButton(
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
                    Hint("低于 30 会按 30 处理 —— 太快会陷入「息屏 → 亮屏 → 再息屏」的循环。" +
                        "改完立即生效，不需要重启应用。")
                }
            }
        }

        // ---------------- 应用单独设置 ----------------
        item { SmallTitle(text = "应用单独设置") }

        if (apps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = cardPadding,
                ) {
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
            // 整组合成一张卡、行间用分割线：MIUI 设置里列表就是这个样子，
            // 比每个应用各发一张卡紧密，也不会让页面看起来是一堆碎片。
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    apps.forEachIndexed { index, pkg ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(appRowDivider))
                        }
                        key(pkg) {
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
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "添加应用",
                    summary = "向框架申请把更多应用加入作用域",
                    onClick = onAddApp,
                )
            }
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
                            iconNotice = if (next) {
                                Notice(
                                    "桌面图标已隐藏，以后从 LSPosed → 模块 → 长按本模块 →「启动」打开。" +
                                        "图标要等桌面刷新后才会消失，属正常现象。",
                                    NoticeTone.INFO,
                                )
                            } else {
                                Notice("桌面图标已恢复显示。", NoticeTone.OK)
                            }
                        } else {
                            iconNotice = Notice("设置失败，请重试。", NoticeTone.ERROR)
                        }
                    },
                    title = "隐藏桌面图标",
                )
            }
        }

        if (iconNotice != null) {
            item { NoticeCard(notice = iconNotice!!) }
        }
    }
}
