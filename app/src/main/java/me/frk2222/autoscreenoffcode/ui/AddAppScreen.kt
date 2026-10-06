package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.frk2222.autoscreenoffcode.data.Framework
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 向 LSPosed 申请扩充作用域。
 *
 * 列出手机上**全部**已安装应用（不只是有桌面图标的），勾选后统一提交，
 * LSPosed 会弹确认框，同意即可。申请成功后目标 App 需要重启才会被注入。
 */
@Composable
fun AddAppScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = Framework.info.scope
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var result by remember { mutableStateOf<Notice?>(null) }
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
        else allApps.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
    }

    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    // 划过一屏左右才出现，刚点进来时不挡视线
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 3 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding,
            verticalArrangement = Arrangement.spacedBy(screenSpacing),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = cardPadding,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                busy = true
                                result = Notice(
                                    "已发起申请，请在 LSPosed 弹出的确认框中同意…",
                                    NoticeTone.INFO,
                                )
                                requestScope(context, selected.toList()) { msg ->
                                    busy = false
                                    result = scopeNotice(msg)
                                    selected = emptySet()
                                }
                            },
                            enabled = selected.isNotEmpty() && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (selected.isEmpty()) "请先勾选应用"
                                else "添加 ${selected.size} 个应用到作用域",
                            )
                        }
                        Hint("申请成功后，目标应用要重启才会被注入。")
                    }
                }
            }

            if (result != null) {
                item { NoticeCard(notice = result!!) }
            }

            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索应用名或包名",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!loading) {
                item {
                    SmallTitle(text = if (visible.isEmpty()) "没有匹配的应用" else "已安装应用")
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

        AnimatedVisibility(
            visible = showBackToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            FloatingActionButton(
                onClick = { uiScope.launch { listState.animateScrollToItem(0) } },
                minWidth = 48.dp,
                minHeight = 48.dp,
            ) {
                Icon(
                    imageVector = rememberArrowUp(),
                    contentDescription = "回到顶部",
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private data class AppEntry(val pkg: String, val label: String, val hasLauncher: Boolean)

/**
 * 列出手机上**所有**已安装的应用。
 *
 * 不只看有桌面图标的：很多目标（以及系统组件）没有 launcher activity，
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
