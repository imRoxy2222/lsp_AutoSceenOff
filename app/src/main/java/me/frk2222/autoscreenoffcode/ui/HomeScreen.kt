package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.frk2222.autoscreenoffcode.data.ConfigStore
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.data.FrameworkInfo
import me.frk2222.autoscreenoffcode.xposed.Config
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 首页：一眼看清模块现在能不能用、不能用的话缺哪一步，然后把最该点的两件事摆在手边。
 *
 * 配色约定（避免到处临时取色导致混乱）：
 * - 一张卡里**最多一个语义色**，只给「结论」用（状态圆点 / 大标题 / 徽章 / 提示条）；
 * - 说明文字一律走 [Hint]（次要灰），不要用语义色；
 * - 信息清单的取值，只有「支持 / 不支持」这种二值判断才上色，其余用正文色。
 */
@Composable
fun HomeScreen(
    padding: PaddingValues,
    onGoConfig: () -> Unit,
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

    val appCount = remember(info) { info.scope.count { it != "system" } }
    val globalValue = remember(rev) { ConfigStore.int(Config.KEY_GLOBAL_VALUE, Config.DEFAULT_VALUE) }
    val globalUnit = remember(rev) {
        Config.TimeUnit.fromKey(ConfigStore.string(Config.KEY_GLOBAL_UNIT, Config.DEFAULT_UNIT)).label
    }

    /** 操作结果。tone 决定提示条底色，别用灰色小字糊过去 */
    var notice by remember { mutableStateOf<Notice?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(screenSpacing),
    ) {
        // ---- 状态：一眼结论 ----
        item {
            val state = remember(activated, info, enabled, systemEnabled, dryRun, appCount) {
                serviceState(
                    activated = activated,
                    enabled = enabled,
                    hasSystemScope = info.scope.contains("system"),
                    systemEnabled = systemEnabled,
                    dryRun = dryRun,
                    appCount = appCount,
                )
            }
            StatusCard(state = state, info = info, appCount = appCount)
        }

        // ---- 当前配置：只读，改要去「配置」标签 ----
        item { SmallTitle(text = "当前配置") }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = cardPadding,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Hint("全局无操作时长")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$globalValue $globalUnit",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = tonePrimary(),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    StatusBadge(
                        text = if (enabled) "计时中" else "已停用",
                        color = if (enabled) toneOk() else toneMuted(),
                    )
                }
            }
        }

        // ---- 快捷操作：首页只放「必须在这里点」的两件事 ----
        item { SmallTitle(text = "快捷操作") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        checked = systemEnabled,
                        onCheckedChange = { next ->
                            notice = if (ConfigStore.put(Config.KEY_SYSTEM_ENABLED, next)) {
                                if (next) {
                                    Notice(
                                        "已开启，system_server 会在 30 秒内完成注册",
                                        NoticeTone.OK,
                                    )
                                } else {
                                    Notice("已关闭，息屏请求将无人执行", NoticeTone.WARN)
                                }
                            } else {
                                Notice(
                                    "写入失败：模块未激活，请先在 LSPosed 里启用本模块并勾选「系统框架」",
                                    NoticeTone.ERROR,
                                )
                            }
                        },
                        title = "① 启用息屏功能(必开)",
                        summary = "息屏必须由系统进程执行，不开这个所有请求都没人处理",
                        enabled = activated,
                    )
                    HorizontalDivider(modifier = Modifier.padding(dividerPadding))
                    SwitchPreference(
                        checked = dryRun,
                        onCheckedChange = { next ->
                            notice = if (ConfigStore.put(Config.KEY_DRY_RUN, next)) {
                                if (next) {
                                    Notice("已回到安全模式：只写日志，不会真的息屏", NoticeTone.WARN)
                                } else {
                                    Notice("安全模式已关闭，功能正式生效", NoticeTone.OK)
                                }
                            } else {
                                Notice("写入失败：模块未激活", NoticeTone.ERROR)
                            }
                        },
                        title = "② 安全模式（DEBUG）",
                        summary = "仅限DEBUG开启,平时需关闭",
                        enabled = activated,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = cardPadding,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Hint("验证链路是否可用，不受安全模式限制")
                    Button(
                        onClick = { testScreenOff(context) { notice = it } },
                        enabled = activated,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("立即测试息屏")
                    }
                }
            }
        }

        if (notice != null) {
            item { NoticeCard(notice = notice!!) }
        }

        // ---- 其余设置都在「配置」标签 ----
        item { SmallTitle(text = "更多设置") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "调时长 · 加应用",
                    summary = "全局默认时长、单个应用单独设置",
                    onClick = onGoConfig,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 状态卡

/** 模块当前处于哪一档状态。首页所有结论都由它推导 */
private enum class ServiceState {
    INACTIVE, NO_SYSTEM_SCOPE, SYSTEM_OFF, DISABLED, SAFE_MODE, NO_APP, RUNNING,
}

private fun serviceState(
    activated: Boolean,
    enabled: Boolean,
    hasSystemScope: Boolean,
    systemEnabled: Boolean,
    dryRun: Boolean,
    appCount: Int,
): ServiceState = when {
    !activated -> ServiceState.INACTIVE
    !hasSystemScope -> ServiceState.NO_SYSTEM_SCOPE
    !systemEnabled -> ServiceState.SYSTEM_OFF
    !enabled -> ServiceState.DISABLED
    dryRun -> ServiceState.SAFE_MODE
    appCount == 0 -> ServiceState.NO_APP
    else -> ServiceState.RUNNING
}

@Composable
private fun StatusCard(
    state: ServiceState,
    info: FrameworkInfo,
    appCount: Int,
) {
    val (headline, detail, tone) = stateText(state, appCount)

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = cardPadding,
    ) {
        // 结论区：语义色只出现在这里（圆点 + 大标题 + 徽章）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = tone, size = 10.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = headline,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = tone,
            )
            Spacer(modifier = Modifier.width(10.dp))
            StatusBadge(text = state.badge(), color = tone)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Hint(detail)

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()

        // 信息清单：只有二值判断才上色，其余走正文色
        Spacer(modifier = Modifier.height(10.dp))
        if (state == ServiceState.INACTIVE) {
            InfoRow("框架", "未连接", valueColor = toneError())
        } else {
            InfoRow("框架", "${info.name} ${info.version}".trim().ifEmpty { "已连接" })
            InfoRow("Xposed API", if (info.apiVersion == 0) "-" else info.apiVersion.toString())
            InfoRow(
                label = "息屏能力(system)",
                value = if (info.hasSystemCapability) "支持" else "不支持",
                valueColor = if (info.hasSystemCapability) toneOk() else toneError(),
            )
            InfoRow(
                label = "远程配置(remote)",
                value = if (info.hasRemoteCapability) "支持" else "不支持",
                valueColor = if (info.hasRemoteCapability) toneOk() else toneError(),
            )
            InfoRow("已生效作用域", "$appCount 个应用")
        }
    }
}

/** 状态卡右上角那个徽章的词。短一点，长了会把标题挤变形 */
private fun ServiceState.badge(): String = when (this) {
    ServiceState.INACTIVE -> "待处理"
    ServiceState.NO_SYSTEM_SCOPE -> "待处理"
    ServiceState.SYSTEM_OFF -> "待处理"
    ServiceState.DISABLED -> "已关闭"
    ServiceState.SAFE_MODE -> "演练中"
    ServiceState.NO_APP -> "待处理"
    ServiceState.RUNNING -> "工作中"
}

@Composable
private fun stateText(state: ServiceState, appCount: Int): Triple<String, String, Color> {
    val muted = toneMuted()
    return when (state) {
        ServiceState.INACTIVE -> Triple(
            "模块未激活",
            "在 LSPosed 里启用本模块，作用域勾选「系统框架」和目标应用，再重启这些应用。",
            toneError(),
        )

        ServiceState.NO_SYSTEM_SCOPE -> Triple(
            "缺少系统框架",
            "作用域里没有「系统框架」。息屏必须由它执行，请到 LSPosed 补勾。",
            toneError(),
        )

        ServiceState.SYSTEM_OFF -> Triple(
            "缺少关键开关",
            "打开下面的「① 启用系统框架息屏」，最多 30 秒后生效，不需要重启。",
            toneWarn(),
        )

        ServiceState.DISABLED -> Triple(
            "已停用",
            "总开关关闭中，不做任何计时。到「配置」里重新打开。",
            muted,
        )

        ServiceState.SAFE_MODE -> Triple(
            "安全模式",
            "运行链路正常，但只会写日志不会真的息屏。先点「立即测试息屏」，确认能关屏后关掉 ②。",
            toneWarn(),
        )

        ServiceState.NO_APP -> Triple(
            "等待生效",
            "作用域里还没有应用。到「配置」里添加要托管的应用（例如抖音 / 快手 / 红果短剧）。",
            toneWarn(),
        )

        ServiceState.RUNNING -> Triple(
            "已生效",
            "共 $appCount 个应用在管理范围内，无操作到时会自动息屏。",
            toneOk(),
        )
    }
}

// ------------------------------------------------------------------ 息屏自检

/**
 * 点一次就走完「开开关 → 发广播 → 看屏幕到底灭没灭」。
 * 1 秒后自检屏幕状态：没灭就把最可能的几个原因说清楚，省得去翻 logcat。
 */
private fun testScreenOff(context: Context, onResult: (Notice) -> Unit) {
    if (!ConfigStore.bool(Config.KEY_SYSTEM_ENABLED, Config.DEFAULT_SYSTEM_ENABLED)) {
        val ok = ConfigStore.put(Config.KEY_SYSTEM_ENABLED, true)
        onResult(
            if (ok) {
                Notice(
                    "已替你打开「① 启用息屏功能」。system_server 会在 30 秒内完成注册，请等一会儿再点一次。",
                    NoticeTone.INFO,
                )
            } else {
                Notice(
                    "写入配置失败：模块未激活。请先在 LSPosed 里启用本模块并勾选「系统框架」，再重启。",
                    NoticeTone.ERROR,
                )
            },
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
            onResult(Notice("发送广播失败：${it.message}", NoticeTone.ERROR))
            return
        }

    onResult(Notice("已发送，1 秒后自检…", NoticeTone.INFO))

    Handler(Looper.getMainLooper()).postDelayed({
        val pm = runCatching {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }.getOrNull()
        val stillOn = pm == null || runCatching { pm.isInteractive }.getOrDefault(true)
        if (stillOn) {
            onResult(
                Notice(
                    "屏幕没反应 —— system_server 侧没有执行息屏。按顺序检查：" +
                        "① LSPosed 作用域里勾选了「系统框架」；② 勾选后重启过手机；" +
                        "③ 首页「① 启用息屏功能」已打开且等满 30 秒；" +
                        "④ 日志：adb logcat -s AutoScreenOff",
                    NoticeTone.ERROR,
                )
            )
        } else {
            onResult(
                Notice(
                    "息屏成功。现在可以在首页关掉「② 安全模式」，功能就正式启用了。",
                    NoticeTone.OK,
                )
            )
        }
    }, 1000)
}
