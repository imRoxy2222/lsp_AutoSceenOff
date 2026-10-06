package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.frk2222.autoscreenoffcode.data.Framework
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表筛选。
 *
 * 默认 [AppFilter.USER]：一台手机上系统应用常常两三百个，而真正要加进作用域的
 * 基本都是自己装的那些。默认全列出来既翻不到头，也让列表平白多背上几百个待解码的图标。
 */
internal enum class AppFilter(val label: String) {
    USER("用户应用"),
    SYSTEM("系统应用"),
    NO_LAUNCHER("无桌面图标"),
    ALL("全部"),
}

private fun AppEntry.matches(filter: AppFilter): Boolean = when (filter) {
    AppFilter.USER -> !isSystem
    AppFilter.SYSTEM -> isSystem
    AppFilter.NO_LAUNCHER -> !hasLauncher
    AppFilter.ALL -> true
}

/**
 * 顶栏右上角的筛选入口：点一下从按钮下方展开下拉菜单，在里面挑筛选项（微信右上角「+」那种）。
 *
 * 菜单用 MIUIX 自带的 OverlayListPopup + ListPopupColumn + DropdownImpl，
 * 弹出的圆角卡、变暗背景、选中项右侧的对勾都是框架给的，不用自己拼。
 *
 * ★ [OverlayListPopup] 的锚点是它**外层容器的边界**（内部放了个 Spacer 取父布局坐标），
 * 所以必须和触发按钮放在同一个 Box 里，否则菜单会跑到屏幕角落去；
 * 右上角的按钮配 `Align.End`，菜单右边缘就和按钮右边缘对齐。
 *
 * 当前不是默认筛选（用户应用）时图标染成主色，提醒「你现在看的是筛过的列表」。
 */
@Composable
internal fun AppFilterMenu(
    filter: AppFilter,
    onFilterChange: (AppFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = filter != AppFilter.USER

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MiuixIcons.Filter,
                contentDescription = "筛选",
                tint = if (filtered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            )
        }
        OverlayListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
        ) {
            ListPopupColumn {
                val options = AppFilter.entries
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = option.label,
                        optionSize = options.size,
                        isSelected = option == filter,
                        index = index,
                        onSelectedIndexChange = {
                            onFilterChange(options[it])
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * 向 LSPosed 申请扩充作用域。
 *
 * 列出手机上**全部**已安装应用（不只是有桌面图标的），勾选后统一提交，
 * LSPosed 会弹确认框，同意即可。申请成功后目标 App 需要重启才会被注入。
 */
@Composable
internal fun AddAppScreen(
    padding: PaddingValues,
    /** 当前筛选条件。由外壳持有，因为改它的按钮在顶栏右上角而不在这个页面里 */
    filter: AppFilter,
    onFilterChange: (AppFilter) -> Unit,
) {
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
    // 筛选 + 搜索一次算完，别在组合里反复跑；几百项的全表过滤放主线程会拖慢每一帧
    val visible = remember(allApps, q, filter) {
        allApps.filter { it.matches(filter) }.let { list ->
            if (q.isEmpty()) list
            else list.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
        }
    }

    /**
     * 预热当前筛选结果的图标。
     *
     * 不预热的话，每划到一个新项都要「先占位字母 → 后台解码 → 回来补图」，
     * 补图那一下是一次重组，一屏十几个项同时补就会顿。预热后滑动基本全是缓存命中。
     * 换筛选条件会重新起一轮，旧的自己退出。
     */
    LaunchedEffect(allApps, filter) {
        if (allApps.isNotEmpty()) {
            preloadIcons(context, allApps.filter { it.matches(filter) }.map { it.pkg })
        }
    }

    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    // 划过一屏左右才出现，刚点进来时不挡视线
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 3 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    // 换筛选条件等于换了整个列表，停在原来的位置没有意义
    LaunchedEffect(filter) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
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

            // ---------------- 搜索 + 筛选 ----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = cardPadding,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "搜索应用名或包名",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!loading) {
                item {
                    SmallTitle(
                        text = if (visible.isEmpty()) "没有匹配的应用"
                        else "${filter.label} · ${visible.size} 个",
                    )
                }
            }

            if (!loading && visible.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = cardPadding,
                    ) {
                        Hint(
                            "当前筛选条件下没有可添加的应用。已经加进作用域的应用不会在这里出现，" +
                                "可以换个标签看看。",
                        )
                    }
                }
            }

            items(visible, key = { it.pkg }) { app ->
                val checked = app.pkg in selected
                BasicComponent(
                    title = app.label,
                    summary = app.summary(),
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

private data class AppEntry(
    val pkg: String,
    val label: String,
    val hasLauncher: Boolean,
    val isSystem: Boolean,
)

/** 副标题：包名 + 需要提醒的属性。让用户明白自己为什么会在这个筛选里看到它 */
private fun AppEntry.summary(): String {
    val tags = buildList {
        if (isSystem) add("系统")
        if (!hasLauncher) add("无桌面图标")
    }
    return if (tags.isEmpty()) pkg else "$pkg（${tags.joinToString(" · ")}）"
}

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
        // 直接用手里这份 ApplicationInfo 取名字，别再 getApplicationInfo(pkg) 多问一次
        // —— 每个包都是一次跨进程调用，几百个包能省下小半秒
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        list.add(AppEntry(pkg, label, pkg in launcherPkgs, isSystem))
    }
    // 有桌面图标的在前，其余按名称排
    list.sortWith(compareByDescending<AppEntry> { it.hasLauncher }.thenBy { it.label.lowercase() })
    return list
}
