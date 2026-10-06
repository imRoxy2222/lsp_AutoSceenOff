package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
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
            val label = remember(pkg) { appLabel(context, pkg) }
            ArrowPreference(
                title = label,
                summary = summary,
                startAction = { AppIcon(pkg = pkg, label = label) },
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
fun AppDetailScreen(padding: PaddingValues, pkg: String, onRemoved: () -> Unit) {
    val context = LocalContext.current
    val rev = ConfigStore.revision
    var removeResult by remember { mutableStateOf<String?>(null) }
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

        item { SmallTitle(text = "作用域") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("把这个应用从模块作用域里移除，之后它不再受本模块管理（也可以在 LSPosed 里取消勾选）。")
                    Button(
                        onClick = {
                            removeScope(context, pkg) { msg ->
                                removeResult = msg
                                if (msg.startsWith("已把")) onRemoved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("从作用域移除")
                    }
                    if (removeResult != null) Text(removeResult!!)
                }
            }
        }
    }
}

private enum class Mode { FOLLOW, CUSTOM, OFF }

/**
 * 向框架申请把应用加入作用域。
 *
 * 流程：App 勾选 → 调 service.requestScope() → LSPosed 弹确认框 → 用户同意 →
 * 回调 onScopeRequestApproved → 框架自动把该应用勾进本模块的作用域。
 * 注意：申请成功后目标 App 需要重启（强行停止）才会被注入。
 */
@Composable
fun AddAppScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = Framework.info.scope
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue("")) }

    DisposableEffect(Unit) {
        val thread = Thread {
            val list = loadInstalledApps(context, scope)
            runOnMain {
                allApps = list
                loading = false
            }
        }
        thread.start()
        onDispose { thread.interrupt() }
    }

    val q = query.text.trim().lowercase()
    val visible = remember(allApps, q) {
        if (q.isEmpty()) allApps
        else allApps.filter {
            it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "正在读取应用列表…" else "手机上共 ${allApps.size} 个应用可添加")
                Text("这里列出的是手机上全部已安装应用，不受推荐列表限制。勾选后点下面的按钮向 LSPosed 申请，在它弹出的确认框里同意即可。")
            }
        }

        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索应用名或包名",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Button(
                        onClick = {
                            busy = true
                            result = "已发起申请，请在 LSPosed 弹出的确认框中同意…"
                            requestScope(context, selected.toList()) { msg ->
                                busy = false
                                result = msg
                                selected = emptySet()
                            }
                        },
                        enabled = selected.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (selected.isEmpty()) "请先勾选应用"
                            else "向框架申请添加 ${selected.size} 个应用"
                        )
                    }
                    if (result != null) Text(result!!)
                }
            }
        }

        items(visible, key = { it.pkg }) { app ->
            val checked = app.pkg in selected
            BasicComponent(
                title = app.label,
                summary = if (app.hasLauncher) app.pkg else "${app.pkg}（无桌面图标）",
                startAction = { AppIcon(pkg = app.pkg, label = app.label) },
                endActions = {
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                    )
                },
                onClick = {
                    selected = if (checked) selected - app.pkg else selected + app.pkg
                },
            )
        }
    }
}

/**
 * 列出手机上**所有**已安装的应用。
 *
 * 不再只看有桌面图标的：很多目标（以及系统组件）没有 launcher activity，
 * 但照样可以被勾进作用域并被 hook。有桌面图标的排在前面，方便找。
 *
 * 注意：需要 manifest 里声明 QUERY_ALL_PACKAGES + <queries>，
 * 否则 Android 11+ 的包可见性限制会让这里只返回寥寥几个。
 */
private fun loadInstalledApps(context: Context, scope: List<String>): List<AppEntry> {
    val pm = context.packageManager
    val self = context.packageName

    val launcherPkgs = mutableSetOf<String>()
    runCatching {
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(probe, 0).mapNotNullTo(launcherPkgs) { it.activityInfo?.packageName }
    }

    val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())

    val list = ArrayList<AppEntry>(installed.size)
    for (info in installed) {
        val pkg = info.packageName
        if (pkg == self || pkg in scope) continue
        list.add(AppEntry(pkg, appLabel(context, pkg), pkg in launcherPkgs))
    }
    // 有桌面图标的在前，其余按名称排
    list.sortWith(compareByDescending<AppEntry> { it.hasLauncher }.thenBy { it.label.lowercase() })
    return list
}

private data class AppEntry(val pkg: String, val label: String, val hasLauncher: Boolean)

private fun requestScope(context: Context, pkgs: List<String>, onResult: (String) -> Unit) {
    val service = Framework.service
    if (service == null) {
        onResult("模块未激活，无法申请。请先在 LSPosed 里启用本模块。")
        return
    }
    runCatching {
        service.requestScope(pkgs, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                runOnMain {
                    Framework.refresh()
                    onResult(
                        "已加入作用域 ${approved.size} 个：${approved.joinToString()}。" +
                            "请强行停止（或重启）这些应用，模块才会注入生效。"
                    )
                }
            }

            override fun onScopeRequestFailed(message: String) {
                runOnMain { onResult("申请失败：$message") }
            }
        })
    }.onFailure {
        runOnMain { onResult("请求异常：${it.message}") }
    }
}

/** 反向操作：把应用从本模块作用域里移除，之后它不再受本模块管理 */
private fun removeScope(context: Context, pkg: String, onResult: (String) -> Unit) {
    val service = Framework.service
    if (service == null) {
        onResult("模块未激活，无法移除。")
        return
    }
    runCatching { service.removeScope(listOf(pkg)) }
        .onSuccess {
            runOnMain {
                Framework.refresh()
                onResult("已把 ${appLabel(context, pkg)} 移出作用域。")
            }
        }
        .onFailure { runOnMain { onResult("移除失败：${it.message}") } }
}
