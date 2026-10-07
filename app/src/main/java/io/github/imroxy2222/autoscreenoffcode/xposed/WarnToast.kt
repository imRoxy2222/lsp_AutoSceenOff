package io.github.imroxy2222.autoscreenoffcode.xposed

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import io.github.imroxy2222.autoscreenoffcode.ui.theme.AppTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Timer
import kotlin.time.Duration.Companion.milliseconds

/**
 * 息屏前的「即将息屏」提示条。
 *
 * 两个硬性要求决定了现在这个实现：
 *  1. **一直挂着**，直到用户触屏、或者真的息屏了才收 —— 所以不能用 Snackbar 那种
 *     「到点自动消失」的组件，改成自己画一个居中胶囊，由外部控制显隐。
 *  2. **不能吃掉触摸** —— 用户看到提示后点一下屏幕就能重新计时，所以浮层窗口带
 *     FLAG_NOT_TOUCHABLE，触摸直接穿透给下面的 App。
 *
 * 样式：深色半透明胶囊 + 白色图标与文字（MIUI / iOS 那种浮层 toast 的样子）。
 * 之所以不跟主题色：这个浮层是盖在**别人的 App** 上的，不属于本模块页面，
 * 用固定的深色底才能在浅色视频界面上也看得清。
 *
 * 实现上还有两个坑（都是踩过才知道的）：
 *  - ComposeView 是「野生」挂到窗口上的，必须自己补 ViewTreeLifecycleOwner /
 *    ViewTreeSavedStateRegistryOwner，否则 windowRecomposer 直接抛异常。
 *  - 整段都在 try/catch 里，任何一步失败就退回系统 Toast，
 *    绝不因为一条提示把被 hook 的 App 搞崩。
 */
internal object WarnToast {

    internal const val ENTER_MS = 260
    internal const val EXIT_MS = 200

    /** 倒计时刷新间隔 */
    internal const val TICK_MS = 250L

    /** 兜底：万一没人来收（息屏广播丢了之类），最多挂 3 分钟自己撤，别一直挡着人家的 App */
    private const val MAX_SHOW_MS = 180_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前正弹着的提示条；null 表示没在弹 */
    private var session: Session? = null

    /**
     * 弹出提示条，并**一直显示**直到 [dismiss] 被调用。
     *
     * @param deadlineElapsed 预计息屏的时刻（SystemClock.elapsedRealtime），提示条会自己倒数到 0
     * @return true = 浮层弹成功了；false = 已经退回系统 Toast
     */
    fun show(
        activity: Activity?,
        appContext: Context?,
        deadlineElapsed: Long,
        dryRun: Boolean,
    ): Boolean {
        // 同一时刻只允许一条：先把旧的立刻收掉，再弹新的
        dismissNow()
        val shown = activity?.let {
            runCatching { showOverlay(it, deadlineElapsed, dryRun) }.getOrDefault(false)
        } ?: false
        if (!shown) {
            val ctx = activity ?: appContext
            if (ctx != null) {
                runCatching {
                    Toast.makeText(
                        ctx,
                        if (dryRun) "安全模式：即将息屏（不会真的息屏）" else "即将息屏，动一下屏幕可取消",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        return shown
    }

    /** 收起提示条（带退场动画）。幂等，任意线程都能调 */
    fun dismiss() {
        val s = session ?: return
        session = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal(s)
        } else {
            mainHandler.post { dismissInternal(s) }
        }
    }

    /** 立刻收掉、不等动画。换新的提示条时用 */
    private fun dismissNow() {
        val s = session ?: return
        session = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeWindow(s)
        } else {
            mainHandler.post { removeWindow(s) }
        }
    }

    private fun dismissInternal(s: Session) {
        s.visible.value = false // 交给 Compose 播退场动画
        mainHandler.postDelayed({ removeWindow(s) }, EXIT_MS + 60L)
    }

    /** 在当前 Activity 上开一个透明子窗口，里面放 Compose 画的提示条 */
    private fun showOverlay(activity: Activity, deadline: Long, dryRun: Boolean): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val decor = activity.window?.decorView ?: return false
        val token = decor.windowToken ?: return false

        val owner = OverlayOwner()
        owner.performCreate()
        val visible = mutableStateOf(false)

        val composeView = ComposeView(activity).apply {
            // Owner 挂不上就别硬来了，直接退回系统 Toast：少了 Owner 的 ComposeView
            // 一 attach 就会抛异常，那是在别人的 App 进程里
            if (!setViewTreeOwners(this, owner)) return false
            setContent {
                AppTheme {
                    WarnOverlay(
                        deadlineElapsed = deadline,
                        dryRun = dryRun,
                        visible = visible.value,
                    )
                }
            }
        }

        val wm = activity.windowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.token = token
            title = "AutoScreenOffToast"
        }

        wm.addView(composeView, lp)

        val s = Session(composeView, wm, owner, visible)
        session = s
        visible.value = true // 挂上之后再置 true，进场动画才播得出来
        mainHandler.postDelayed({
            if (session === s) dismiss()
        }, MAX_SHOW_MS)
        return true
    }

    private fun removeWindow(s: Session) {
        runCatching { s.wm.removeViewImmediate(s.view) }
        runCatching { s.view.disposeComposition() }
        s.owner.performDestroy()
    }

    /**
     * 把 Owner 挂到 View 树上。
     *
     * 这里走反射是有原因的：`ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner`
     * 这两个门面类虽然在 classpath 的 AAR 里，但 Kotlin 编译期看不见（KMP 发布的
     * android 变体只把类打进了 classes.jar，没进 .kotlin_module），直接 import 会
     * "Unresolved reference"，只能反射调它们的 set()。
     * 两个类都打进了本模块的 APK，运行时一定找得到；万一找不到就返回 false，退回系统 Toast。
     */
    private fun setViewTreeOwners(view: View, owner: Any): Boolean {
        val lifecycle = setTreeOwner(
            view,
            "androidx.lifecycle.ViewTreeLifecycleOwner",
            LifecycleOwner::class.java,
            owner,
        )
        val savedState = setTreeOwner(
            view,
            "androidx.savedstate.ViewTreeSavedStateRegistryOwner",
            SavedStateRegistryOwner::class.java,
            owner,
        )
        return lifecycle && savedState
    }

    private fun setTreeOwner(view: View, className: String, ownerType: Class<*>, owner: Any): Boolean =
        runCatching {
            Class.forName(className)
                .getDeclaredMethod("set", View::class.java, ownerType)
                .apply { isAccessible = true }
                .invoke(null, view, owner)
            true
        }.getOrDefault(false)

    /**
     * 给「凭空造出来的」ComposeView 补一套 LifecycleOwner + SavedStateRegistryOwner。
     *
     * 用自建的而不是去偷 Activity 的：被 hook 的 App 未必是 ComponentActivity，
     * decorView 上不一定挂着这两个 Owner；自己造一个，生命周期完全可控，
     * 收窗口时直接推到 DESTROYED，不会有残留。
     */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {

        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            field = LifecycleRegistry.createUnsafe(this)
        override val savedStateRegistry get() = controller.savedStateRegistry

        fun performCreate() {
            controller.performRestore(null)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun performDestroy() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private class Session(
        val view: ComposeView,
        val wm: WindowManager,
        val owner: OverlayOwner,
        val visible: MutableState<Boolean>,
    )
}

private val CapsuleShape = RoundedCornerShape(percent = 50)

private val CapsuleBg = Color(0xF01C1C1E)

private fun remainingSeconds(deadlineElapsed: Long): Long =
    ((deadlineElapsed - SystemClock.elapsedRealtime()) + 999L).coerceAtLeast(0L) / 1000L

/** 提示条本体：全屏透明容器 + 屏幕中下部的胶囊，[visible] 控制进出场动画 */
@Composable
private fun WarnOverlay(deadlineElapsed: Long, dryRun: Boolean, visible: Boolean) {
    var leftSeconds by remember(deadlineElapsed) {
        mutableLongStateOf(remainingSeconds(deadlineElapsed))
    }
    LaunchedEffect(deadlineElapsed) {
        while (true) {
            leftSeconds = remainingSeconds(deadlineElapsed)
            if (leftSeconds <= 0L) break
            delay(WarnToast.TICK_MS.milliseconds)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            // 放屏幕中下部：MIUI 的 toast 就在这个高度，既醒目又不挡视频正中间
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(WarnToast.ENTER_MS)) +
                    scaleIn(tween(WarnToast.ENTER_MS), initialScale = 0.90f),
            exit = fadeOut(tween(WarnToast.EXIT_MS)) +
                    scaleOut(tween(WarnToast.EXIT_MS), targetScale = 0.94f),
        ) {
            Capsule(leftSeconds = leftSeconds, dryRun = dryRun)
        }
    }
}

/** 深色半透明胶囊：图标 + 主文案（带倒计时）+ 副文案 */
@Composable
private fun Capsule(leftSeconds: Long, dryRun: Boolean) {
    Box(
        modifier = Modifier
            .shadow(elevation = 12.dp, shape = CapsuleShape)
            .background(color = CapsuleBg, shape = CapsuleShape)
            // 一圈极淡的白色描边，让胶囊在深色视频画面上也能看出边界
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.10f), shape = CapsuleShape)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Timer,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (leftSeconds > 0L) "即将息屏 · $leftSeconds 秒" else "即将息屏",
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
