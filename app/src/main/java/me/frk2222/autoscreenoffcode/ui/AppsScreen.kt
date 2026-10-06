package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

private val mainHandler = Handler(Looper.getMainLooper())

private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}

internal fun appLabel(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (t: Throwable) {
    pkg
}

@Composable
fun AppsScreen(
    padding: PaddingValues,
    onOpenApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    val context = LocalContext.current
    val allScope = Framework.info.scope
    val scope = allScope.filter { it != "system" }
    val prefs = ConfigStore.snapshot()
    val rev = ConfigStore.revision

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (!allScope.contains("system")) {
                    Text("作用域里没有「系统框架」")
                    Text("没有它就无法执行息屏。请到 LSPosed 的作用域中勾选「系统框架」。")
                } else if (scope.isEmpty()) {
                    Text("还没有可用的应用")
                    Text("请先在 LSPosed 作用域里勾选应用（例如抖音、快手），或点下面的「添加应用」。")
                } else {
                    Text("共 ${scope.size} 个应用已生效，点击进入单独设置")
                }
            }
        }

        items(scope, key = { it }) { pkg ->
            val summary = remember(rev, pkg) {
                if (prefs != null) Config.effectiveText(prefs, pkg) else "模块未激活"
            }
            ArrowPreference(
                title = appLabel(context, pkg),
                summary = summary,
                onClick = { onOpenApp(pkg) },
            )
        }

        item {
            ArrowPreference(
                title = "添加应用",
                summary = "向框架申请把更多应用加入作用域",
                onClick = onAddApp,
            )
        }
    }
}

@Composable
fun AppDetailScreen(padding: PaddingValues, pkg: String) {
    val rev = ConfigStore.revision
    val key = Config.PREFIX_APP + pkg
    val raw = remember(rev, pkg) { ConfigStore.string(key, "") }
    val prefs = ConfigStore.snapshot()

    val mode = when (raw) {
        Config.VALUE_OFF -> Mode.OFF
        "" -> Mode.FOLLOW
        else -> Mode.CUSTOM
    }

    val parsedValue = raw.substringBefore('|').toIntOrNull()
        ?: ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE)
    val parsedUnit = if (raw.contains('|')) raw.substringAfter('|') else Config.DEFAULT_UNIT

    var valueText by remember(rev, pkg) { mutableStateOf(parsedValue.toString()) }
    var unitIndex by remember(rev, pkg) { mutableStateOf(Config.TimeUnit.indexOfKey(parsedUnit)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SmallTitle(text = pkg) }

        item {
            RadioButtonPreference(
                title = "跟随全局",
                summary = if (prefs != null) "当前全局：${Config.globalText(prefs)}" else null,
                selected = mode == Mode.FOLLOW,
                onClick = { ConfigStore.remove(key) },
            )
        }

        item {
            RadioButtonPreference(
                title = "单独设置",
                summary = "只对这个应用生效的时长",
                selected = mode == Mode.CUSTOM,
                onClick = {
                    val v = ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE)
                    val u = ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)
                    ConfigStore.put(key, Config.encode(v.toLong(), Config.TimeUnit.fromKey(u)))
                },
            )
        }

        item {
            RadioButtonPreference(
                title = "该应用不生效",
                summary = "无论全局怎么设置，这个应用都不自动息屏",
                selected = mode == Mode.OFF,
                onClick = { ConfigStore.put(key, Config.VALUE_OFF) },
            )
        }

        if (mode == Mode.CUSTOM) {
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
                                    ConfigStore.put(
                                        key,
                                        Config.encode(parsed.toLong(), Config.TimeUnit.entries[unitIndex])
                                    )
                                }
                            },
                            label = "数值",
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
                    onSelectedIndexChange = { index ->
                        unitIndex = index
                        val v = valueText.toIntOrNull() ?: Config.DEFAULT_VALUE
                        ConfigStore.put(key, Config.encode(v.toLong(), Config.TimeUnit.entries[index]))
                    },
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("规则：这个应用在前台时，超过设定时长没有任何触摸或按键操作就息屏；不在乎是否在播放视频。")
            }
        }
    }
}

private enum class Mode { FOLLOW, CUSTOM, OFF }

@Composable
fun AddAppScreen(padding: PaddingValues, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = Framework.info.scope
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val thread = Thread {
            val list = loadInstalledApps(context, scope)
            runOnMain {
                apps = list
                loading = false
            }
        }
        thread.start()
        onDispose { thread.interrupt() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "正在读取应用列表…" else "共 ${apps.size} 个可添加的应用")
                Text("点击后向框架发起申请，需要在弹出的确认框里同意。")
            }
        }

        items(apps, key = { it.first }) { (pkg, label) ->
            ArrowPreference(
                title = label,
                summary = pkg,
                onClick = { requestScope(context, pkg, onDone) },
            )
        }
    }
}

private fun loadInstalledApps(context: Context, scope: List<String>): List<Pair<String, String>> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching { pm.queryIntentActivities(launcher, 0) }
        .getOrDefault(emptyList())
        .mapNotNull { it.activityInfo?.packageName }
        .filter { it !in scope && it != context.packageName }
        .distinct()
        .map { it to appLabel(context, it) }
        .sortedBy { it.second.lowercase() }
}

private fun requestScope(context: Context, pkg: String, onDone: () -> Unit) {
    val service = Framework.service
    if (service == null) {
        Toast.makeText(context, "模块未激活", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        service.requestScope(listOf(pkg), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                runOnMain {
                    Framework.refresh()
                    Toast.makeText(context, "已添加：${approved.joinToString()}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }

            override fun onScopeRequestFailed(message: String) {
                runOnMain {
                    Toast.makeText(context, "添加失败：$message", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }.onFailure {
        Toast.makeText(context, "请求失败：${it.message}", Toast.LENGTH_SHORT).show()
    }
}
