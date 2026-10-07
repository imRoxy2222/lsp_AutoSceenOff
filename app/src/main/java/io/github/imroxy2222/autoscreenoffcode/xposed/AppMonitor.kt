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

/**
 * 目标 App 进程内的「无操作检测」。
 *
 * 思路：
 *  1. hook Activity.dispatchTouchEvent / dispatchKeyEvent —— 有任何触摸或按键就算一次操作；
 *  2. hook Activity.onResume / onPause —— 只有 App 在前台时才计时（后台时不干扰用户用别的 App）；
 *     同时把「回到前台」也算作一次操作，这样亮屏后重新计时；
 *  3. 每 5 秒检查一次：距上次操作是否已超过设定时长，超过就发广播让 system_server 息屏。
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

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    fun install() {
        val activity = Class.forName("android.app.Activity", false, classLoader)

        hookSafely("dispatchTouchEvent") {
            val m = activity.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java)
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.getThisObject())
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    chain.proceed()
                }
        }

        hookSafely("dispatchKeyEvent") {
            val m = activity.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java)
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.getThisObject())
                    lastInteractionMs = SystemClock.elapsedRealtime()
                    chain.proceed()
                }
        }

        hookSafely("onResume") {
            val m = activity.getDeclaredMethod("onResume")
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    captureContext(chain.getThisObject())
                    val result = chain.proceed()
                    resumedCount++
                    lastInteractionMs = SystemClock.elapsedRealtime()
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
                } else {
                    checkTimeout()
                }
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

        val now = SystemClock.elapsedRealtime()
        val idle = now - lastInteractionMs
        if (idle < timeout) {
            if (debug) logd("已 ${idle / 1000}s / ${timeout / 1000}s")
            return
        }

        lastInteractionMs = now
        if (p.getBoolean(Config.KEY_DRY_RUN, Config.DEFAULT_DRY_RUN)) {
            logi("[安全模式] $pkg 已 ${idle / 1000}s 无操作，本应息屏（未执行）")
            return
        }
        logi("$pkg 已 ${idle / 1000}s 无操作，请求息屏")
        requestScreenOff(p)
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
        if (appContext == null) {
            appContext = (thisObject as? Activity)?.applicationContext
        }
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
    }
}
