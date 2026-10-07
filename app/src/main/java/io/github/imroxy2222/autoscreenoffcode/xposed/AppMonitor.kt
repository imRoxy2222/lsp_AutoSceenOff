package io.github.imroxy2222.autoscreenoffcode.xposed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * 目标 App 进程内的「无操作检测」。
 *
 * 思路：
 *  1. hook Activity.dispatchTouchEvent / dispatchKeyEvent —— 有任何触摸或按键就算一次操作；
 *  2. hook Activity.onResume / onPause —— 只有 App 在前台时才计时（后台时不干扰用户用别的 App）；
 *     同时把「回到前台」也算作一次操作，这样亮屏后重新计时；
 *  3. 每 5 秒检查一次：距上次操作是否已超过设定时长，超过就发广播让 system_server 息屏；
 *  4. 距离息屏只剩两次检测（约 10 秒）时先弹一条提示条，用户动一下屏幕就能取消。
 *
 * 这里**不在乎**是否在播放视频、有没有 WakeLock，只看有没有操作。
 */
class AppMonitor(
    private val module: XposedModule,
    private val pkg: String,
    private val classLoader: ClassLoader,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { onTick() }

    @Volatile
    private var lastInteractionMs = SystemClock.elapsedRealtime()

    @Volatile
    private var resumedCount = 0

    /** 本轮「已经弹过息屏提醒」，用户一动屏幕就清掉，下次进窗口还能再弹 */
    @Volatile
    private var warned = false

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    /** 最近的那个 Activity，只留弱引用，别把人家的页面拽住不放 */
    private var activityRef: WeakReference<Activity>? = null

    fun install() {
        val activity = Class.forName("android.app.Activity", false, classLoader)

        hookSafely("dispatchTouchEvent") {
            val m = activity.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java)
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.thisObject)
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    WarnToast.dismiss()
                    chain.proceed()
                }
        }

        hookSafely("dispatchKeyEvent") {
            val m = activity.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java)
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.thisObject)
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    WarnToast.dismiss()
                    chain.proceed()
                }
        }

        hookSafely("onResume") {
            val m = activity.getDeclaredMethod("onResume")
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.thisObject)
                    val result = chain.proceed()
                    resumedCount++
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    // 回到前台等于一次操作，提示条该收了
                    WarnToast.dismiss()
                    result
                }
        }

        hookSafely("onPause") {
            val m = activity.getDeclaredMethod("onPause")
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    if (resumedCount > 0) resumedCount--
                    // 退到后台就别把提示条留在人家屏幕上
                    WarnToast.dismiss()
                    result
                }
        }

        handler.postDelayed(ticker, TICK_MS)
        logd("已安装无操作检测")
    }

    private fun onTick() {
        try {
            if (resumedCount > 0) {
                val pm = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null && !pm.isInteractive) {
                    // 屏幕已经是关的，重置计时，等下次亮屏再算
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    WarnToast.dismiss()
                } else {
                    checkTimeout()
                }
            } else {
                // 不在前台（比如被别的页面盖住），提示条收掉，别挡着别人
                WarnToast.dismiss()
            }
        } catch (t: Throwable) {
            logw("定时检测异常：${t.message}")
        }
        handler.postDelayed(ticker, TICK_MS)
    }

    private fun checkTimeout() {
        val p = prefs ?: module.getRemotePreferences(Config.PREFS_GROUP).also { prefs = it }
        val debug = p.getBoolean(Config.KEY_DEBUG, false)
        if (!p.getBoolean(Config.KEY_ENABLED, true)) return

        val timeout = Config.timeoutMs(p, pkg)
        if (timeout <= 0L) {
            if (debug) logd("本应用超时为关闭状态，跳过")
            return
        }

        val dryRun = p.getBoolean(Config.KEY_DRY_RUN, Config.DEFAULT_DRY_RUN)
        val now = SystemClock.elapsedRealtime()
        val idle = now - lastInteractionMs
        val remain = timeout - idle

        // 还没进预警窗口：把「已提醒」标记清掉，用户动过屏幕后下次再进窗口还能提醒；
        // 顺手收一下提示条（触屏那几个 hook 已经收过了，这里是兜底）
        if (remain > WARN_AHEAD_MS) {
            warned = false
            WarnToast.dismiss()
            if (debug) logd("已 ${idle / 1000}s / ${timeout / 1000}s")
            return
        }

        // 进入预警窗口：距息屏只剩两次检测（约 10 秒）。提示条弹出来就一直挂着，
        // 直到用户动一下屏幕（那几个 hook 里收）或者真的息屏（下面收）
        if (remain > 0L) {
            if (!warned && p.getBoolean(Config.KEY_WARN, Config.DEFAULT_WARN)) {
                warned = true
                // 传预计息屏的时刻，提示条自己倒数到 0
                val ok = WarnToast.show(
                    foregroundActivity(),
                    appContext,
                    lastInteractionMs + timeout,
                    dryRun,
                )
                if (ok) {
                    if (debug) logd("已弹出息屏提醒，剩余约 ${remain / 1000}s")
                } else {
                    logw("息屏提醒没弹出来（已退回系统 Toast）")
                }
            }
            return
        }

        // 到点了：先收提示条（息屏后它没意义了，也不能留在下次亮屏的画面上），
        // 再息屏，并把提醒标记复位，等下一轮重新计时
        warned = false
        WarnToast.dismiss()
        lastInteractionMs = now
        if (dryRun) {
            logi("[安全模式] $pkg 已 ${idle / 1000}s 无操作，本应息屏（未执行）")
            return
        }
        logi("$pkg 已 ${idle / 1000}s 无操作，请求息屏")
        requestScreenOff(p)
    }

    /** 当前还活着的前台 Activity，拿不到就返回 null（调用方会退回系统 Toast） */
    private fun foregroundActivity(): Activity? {
        val act = activityRef?.get() ?: return null
        if (act.isFinishing || act.isDestroyed) return null
        return act
    }

    private fun requestScreenOff(p: SharedPreferences) {
        val ctx = appContext
        if (ctx == null) {
            logw("尚未拿到 Context，无法发送息屏广播")
            return
        }
        val intent = Intent(Config.ACTION_SCREEN_OFF)
        intent.putExtra(Config.EXTRA_PACKAGE, pkg)
        intent.putExtra(Config.EXTRA_TOKEN, p.getString(Config.KEY_TOKEN, "") ?: "")
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        runCatching { ctx.sendBroadcast(intent) }
            .onFailure { logw("发送息屏广播失败：${it.message}") }
    }

    private fun captureContext(thisObject: Any?) {
        val act = thisObject as? Activity ?: return
        if (appContext == null) {
            appContext = act.applicationContext
        }
        activityRef = WeakReference(act)
    }

    private fun hookSafely(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            logw("hook $name 失败：${t.message}")
        }
    }

    private fun logd(msg: String) = log(Log.DEBUG, msg)
    private fun logi(msg: String) = log(Log.INFO, msg)
    private fun logw(msg: String) = log(Log.WARN, msg)

    private fun log(priority: Int, msg: String) {
        module.log(priority, Config.TAG, "[$pkg] $msg")
    }

    private companion object {
        const val TICK_MS = 5_000L

        /** 提前多久弹提醒：两次检测，也就是 10 秒左右 */
        const val WARN_AHEAD_MS = 2 * TICK_MS
    }
}
