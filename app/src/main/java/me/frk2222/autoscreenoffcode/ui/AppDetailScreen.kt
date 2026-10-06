package me.frk2222.autoscreenoffcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

/** 单个应用的单独设置 */
@Composable
fun AppDetailScreen(
    padding: PaddingValues,
    pkg: String,
    onRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val rev = ConfigStore.revision
    val key = Config.PREFIX_APP + pkg
    val raw = remember(rev, pkg) { ConfigStore.string(key, "") }
    val prefs = remember(rev) { ConfigStore.snapshot() }

    val mode = when (raw) {
        Config.VALUE_OFF -> AppMode.OFF
        "" -> AppMode.FOLLOW
        else -> AppMode.CUSTOM
    }

    val parsedValue = raw.substringBefore('|').toIntOrNull()
        ?: ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE)
    val parsedUnit = if (raw.contains('|')) raw.substringAfter('|') else Config.DEFAULT_UNIT

    var valueText by remember(rev, pkg) { mutableStateOf(parsedValue.toString()) }
    var unitIndex by remember(rev, pkg) { mutableStateOf(Config.TimeUnit.indexOfKey(parsedUnit)) }
    var removeResult by remember { mutableStateOf<Notice?>(null) }

    val label = remember(pkg) { appLabel(context, pkg) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(screenSpacing),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = cardPadding,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg = pkg, label = label, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Hint(pkg)
                    }
                }
            }
        }

        item { SmallTitle(text = "接管规则") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    RadioButtonPreference(
                        title = "跟随全局",
                        summary = prefs?.let { "当前全局：${Config.globalText(it)}" } ?: "模块未激活",
                        selected = mode == AppMode.FOLLOW,
                        onClick = { ConfigStore.remove(key) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(dividerPadding))
                    RadioButtonPreference(
                        title = "单独设置",
                        summary = "只对这个应用生效的时长",
                        selected = mode == AppMode.CUSTOM,
                        onClick = {
                            val v = ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE)
                            val u = ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)
                            ConfigStore.put(key, Config.encode(v.toLong(), Config.TimeUnit.fromKey(u)))
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(dividerPadding))
                    RadioButtonPreference(
                        title = "该应用不生效",
                        summary = "无论全局怎么设置，这个应用都不自动息屏",
                        selected = mode == AppMode.OFF,
                        onClick = { ConfigStore.put(key, Config.VALUE_OFF) },
                    )
                }
            }
        }

        if (mode == AppMode.CUSTOM) {
            item { SmallTitle(text = "单独时长") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = cardPadding,
                ) {
                    TextField(
                        value = TextFieldValue(valueText),
                        onValueChange = { next ->
                            val filtered = next.text.filter { it.isDigit() }.take(4)
                            valueText = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                ConfigStore.put(
                                    key,
                                    Config.encode(parsed.toLong(), Config.TimeUnit.entries[unitIndex]),
                                )
                            }
                        },
                        label = "数值",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        items = Config.TimeUnit.LABELS,
                        selectedIndex = unitIndex,
                        title = "时间单位",
                        onSelectedIndexChange = { index ->
                            unitIndex = index
                            val v = valueText.toIntOrNull() ?: Config.DEFAULT_VALUE
                            ConfigStore.put(key, Config.encode(v.toLong(), Config.TimeUnit.entries[index]))
                        },
                    )
                }
            }
        }

        item { SmallTitle(text = "作用域") }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = cardPadding,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            removeScope(context, pkg) { msg ->
                                removeResult = scopeNotice(msg)
                                if (msg.startsWith("已把")) onRemoved()
                            }
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("从作用域移除")
                    }
                    Hint("移除后要重启这个应用，注入才会真正停下来。")
                }
            }
        }

        if (removeResult != null) {
            item { NoticeCard(notice = removeResult!!) }
        }
    }
}

private enum class AppMode { FOLLOW, CUSTOM, OFF }
