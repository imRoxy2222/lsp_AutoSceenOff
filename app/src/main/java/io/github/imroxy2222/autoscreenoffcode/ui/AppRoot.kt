package io.github.imroxy2222.autoscreenoffcode.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 应用外壳：底部三个标签（首页 / 配置 / 关于），像微信那样。
 *
 * 导航模型：每个标签各自维护一条返回栈，切标签互不影响，
 * 返回键只弹当前标签的栈顶，弹到根才退出 App。
 * 这样从「配置 → 应用详情」切到「首页」再切回来，还停在原来的详情页上。
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 「添加应用」页的筛选条件放在外壳里：改它的按钮在顶栏右上角，不在那个页面内部
    var appFilter by rememberSaveable { mutableStateOf(AppFilter.USER) }

    val homeStack = remember { mutableStateListOf<Route>(Route.Home) }
    val configStack = remember { mutableStateListOf<Route>(Route.Config) }
    val aboutStack = remember { mutableStateListOf<Route>(Route.About) }
    val stacks = remember { listOf(homeStack, configStack, aboutStack) }

    val stack = stacks[tab]
    val route = stack.last()
    val canBack = stack.size > 1

    BackHandler(enabled = canBack) { stack.removeAt(stack.lastIndex) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = titleOf(route, context),
                navigationIcon = {
                    if (canBack) {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    if (route == Route.AddApp) {
                        AppFilterMenu(
                            filter = appFilter,
                            onFilterChange = { appFilter = it },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = MiuixIcons.Home,
                    label = "首页",
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = MiuixIcons.Settings,
                    label = "配置",
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = MiuixIcons.Info,
                    label = "关于",
                )
            }
        },
    ) { padding ->
        when (route) {
            Route.Home -> HomeScreen(
                padding = padding,
                onGoConfig = { tab = 1 },
            )

            Route.Config -> ConfigScreen(
                padding = padding,
                onOpenApp = { pkg -> stack.add(Route.AppDetail(pkg)) },
                onAddApp = { stack.add(Route.AddApp) },
            )

            Route.About -> AboutScreen(padding = padding)

            is Route.AppDetail -> AppDetailScreen(
                padding = padding,
                pkg = route.pkg,
                onRemoved = { stack.removeAt(stack.lastIndex) },
            )

            Route.AddApp -> AddAppScreen(
                padding = padding,
                filter = appFilter,
                onFilterChange = { appFilter = it },
            )
        }
    }
}

/** 一个标签页内的页面。用 sealed class 保证 when 分支穷举 */
sealed interface Route {

    /** 首页标签根部 */
    data object Home : Route

    /** 配置标签根部 */
    data object Config : Route

    /** 关于标签根部 */
    data object About : Route

    /** 单个应用的设置页 */
    data class AppDetail(val pkg: String) : Route

    /** 向框架申请增补作用域 */
    data object AddApp : Route
}

private fun titleOf(route: Route, context: Context): String = when (route) {
    Route.Home -> "无操作息屏"
    Route.Config -> "配置"
    Route.About -> "关于"
    is Route.AppDetail -> appLabel(context, route.pkg)
    Route.AddApp -> "添加应用"
}

/** 列表页统一的左右留白 */
internal val screenPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

/** 列表页统一的卡片间距 */
internal val screenSpacing: Dp = 12.dp
