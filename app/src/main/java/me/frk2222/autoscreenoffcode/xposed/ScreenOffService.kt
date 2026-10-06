package me.frk2222.autoscreenoffcode.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import kotlin.concurrent.thread

/**
 * system_server 侧的执行端。
 *
 * 普通 App 没有 DEVICE_POWER 权限，调不了 PowerManager.goToSleep，
 * 所以必须由 system_server 来执行。这里做的事情：
 *  1. 等开机真正完成（读 sys.boot_completed），绝不在开机过程中抢资源；
 *  2. 拿到 system 的 Context（只反射 currentActivityThread，绝不调 systemMain）；
 *  3. 注册一个动态广播接收器，等目标 App 发来「该息屏了」；
 *  4. 反射调用 PowerManager.goToSleep（@hide，system_server 不受隐藏 API 限制）。
 *
 * 【为什么写得这么啰嗦】
 * system_server 里只要有任何线程抛出未捕获异常，RuntimeInit 的默认处理器就会
 * System.exit()，整机立刻重启 —— 也就是「卡开机」。所以这里的每一条铁律：
 *
 *  铁律一：绝不调用 ActivityThread.systemMain()。
 *          它会 new 一个 ActivityThread 并覆盖 sCurrentActivityThread，
 *          把系统自己那个架空，system_server 必死。
 *          拿不到 ActivityThread 就返回 false，下一轮重试，等系统自己建好。
 *
 *  铁律二：做任何事之前先等开机完成。开机阶段的 AMS / PMS 可能还没就绪，
 *           Early birds 会把 system_server 拖死或撞上 Watchdog。
 *
 *  铁律三：每个我们自己开的线程都必须装 UncaughtExceptionHandler，
 *           并且整个线程体再包一层 try/catch。漏一个就会整机重启。
 *
 *  铁律四：onReceive 跑在 system_server 主线程，绝不能在里面做 IPC。
 *          令牌等跨进程数据一律在后台线程预热好、缓存到 volatile 字段，
 *          onReceive 只读缓存；真正的息屏丢到工作线程去做。
 *
 *  铁律五：默认不注册。必须用户在 App 里明确打开「系统框架息屏」开关。
 *          另留 setprop sys.autoscreenoff.disable=1 的后门，进不了系统时可从
 *          recovery 的 adb shell 关掉（persist. 前缀会持久化）。
 */
class ScreenOffService(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
) {

    @Volatile private var systemContext: Context? = null
    @Volatile private var powerManager: PowerManager? = null

    /** 令牌在后台线程预热，onReceive 只读这里，绝不触发 IPC */
    @Volatile private var cachedToken: String? = null

    /** 上次真正息屏的时间，做最小间隔限流，防止被恶意广播刷屏 */
    @Volatile private var lastSleepAtMs = 0L

    @Volatile private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null || intent.action != Config.ACTION_SCREEN_OFF) return
            guard {
                val from = intent.getStringExtra(Config.EXTRA_PACKAGE) ?: "unknown"
                val expected = cachedToken
                if (!expected.isNullOrEmpty() && expected != intent.getStringExtra(Config.EXTRA_TOKEN)) {
                    logw("令牌不匹配，忽略来自 $from 的请求")
                    return@guard
                }
                // 主线程不做任何可能阻塞的事，丢给工作线程
                safeThread("aso-sleep") { performSleep(from, intent.getBooleanExtra(Config.EXTRA_FORCE, false)) }
            }
        }
    }

    // ---------------------------------------------------------------- 启动

    fun start() {
        guard {
            if (module.getFrameworkProperties() and XposedInterface.PROP_CAP_SYSTEM == 0L) {
                logw("框架未声明 PROP_CAP_SYSTEM，不注册息屏接收器")
                return@guard
            }
            logi("system_server 侧就绪，等待开机完成后再决定是否注册")
            safeThread("aso-boot") { bootstrap() }
        }
    }

    private fun bootstrap() {
        if (isDisabledByProperty()) {
            logw("检测到停用属性，本次开机不注册息屏接收器")
            return
        }

        waitForBootCompleted()
        // 开机完成后再压一压，避开锁屏 / 解锁那一段最忙的时候
        sleepQuietly(POST_BOOT_DELAY_MS)
        if (isDisabledByProperty()) {
            logw("检测到停用属性，放弃注册")
            return
        }

        // 轮询等待用户在 App 里打开开关。之所以要轮询：注册只能在开机流程里做，
        // 而 remote prefs 是跨进程数据，开机那一刻不一定能读到。
        // 轮询不放过夜：只要开关还没开就一直等，用户随时打开都能在几十秒内生效，不用重启。
        var prefFailures = 0
        var round = 0
        while (!registered) {
            round++
            when (val enabled = refreshConfig()) {
                null -> {
                    // 读不到配置。连着失败若干次就按「用户想用」处理，避免功能静默失效
                    prefFailures++
                    logw("读取远程配置失败（第 $prefFailures 次）")
                    if (prefFailures >= PREF_FAILURE_FALLBACK) {
                        logw("远程配置始终读不到，按「已启用」注册（此时跳过令牌校验）")
                        if (tryRegister()) return
                    }
                }
                true -> if (tryRegister()) return
                false -> Unit // 用户没开开关，继续等
            }
            sleepQuietly(if (round <= FAST_POLL_ROUNDS) FAST_POLL_MS else SLOW_POLL_MS)
        }
    }

    /** 读取远程配置。返回 null 表示这次没读到，不要据此做任何事。 */
    private fun refreshConfig(): Boolean? {
        return try {
            val p = module.getRemotePreferences(Config.PREFS_GROUP)
            cachedToken = p.getString(Config.KEY_TOKEN, "") ?: ""
            p.getBoolean(Config.KEY_SYSTEM_ENABLED, Config.DEFAULT_SYSTEM_ENABLED)
        } catch (t: Throwable) {
            logw("读取远程配置失败，本次跳过：${t.message}")
            null
        }
    }

    private fun tryRegister(): Boolean {
        for (attempt in 1..REGISTER_ATTEMPTS) {
            try {
                if (ensureSystemContext() && !registered) {
                    doRegister()
                    registered = true
                    logi("息屏广播接收器注册成功")
                    return true
                }
            } catch (t: Throwable) {
                logw("第 $attempt 次注册失败：${t.message}")
            }
            if (attempt < REGISTER_ATTEMPTS) sleepQuietly(REGISTER_RETRY_MS)
        }
        loge("息屏广播接收器注册失败，已重试 $REGISTER_ATTEMPTS 次")
        return true // 已经尽力了，别再轮询浪费资源
    }

    private fun doRegister() {
        val ctx = systemContext ?: return
        val filter = IntentFilter(Config.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                return
            } catch (t: SecurityException) {
                logw("以 EXPORTED 注册被拒，退回 NOT_EXPORTED：${t.message}")
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                return
            }
        }
        @Suppress("DEPRECATION")
        ctx.registerReceiver(receiver, filter)
    }

    // ---------------------------------------------------------------- 息屏

    private fun performSleep(from: String, force: Boolean) {
        guard {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastSleepAtMs < MIN_SLEEP_INTERVAL_MS) {
                logw("距离上次息屏不足 ${MIN_SLEEP_INTERVAL_MS / 1000}s，忽略来自 $from 的重复请求")
                return@guard
            }
            lastSleepAtMs = now
            val ok = goToSleep()
            logi(if (ok) "已执行息屏（来自 $from）" else "息屏调用失败（来自 $from）")
        }
    }

    private fun goToSleep(): Boolean {
        val pm = powerManager ?: return false
        val now = SystemClock.uptimeMillis()
        val candidates = pm.javaClass.methods.filter { it.name == "goToSleep" }

        val three = candidates.firstOrNull { it.parameterTypes.size == 3 }
        if (three != null) {
            return runCatching {
                three.isAccessible = true
                three.invoke(pm, now, sleepReason(), 0)
                true
            }.getOrElse { logw("三参数 goToSleep 调用失败：${it.message}"); false }
        }

        val one = candidates.firstOrNull { it.parameterTypes.size == 1 }
        if (one != null) {
            return runCatching {
                one.isAccessible = true
                one.invoke(pm, now)
                true
            }.getOrElse { logw("单参数 goToSleep 调用失败：${it.message}"); false }
        }

        logw("没有找到任何 goToSleep 方法")
        return false
    }

    private fun sleepReason(): Int {
        for (name in arrayOf("GO_TO_SLEEP_REASON_APPLICATION", "GO_TO_SLEEP_REASON_TIMEOUT")) {
            val value = runCatching {
                val f = PowerManager::class.java.getDeclaredField(name)
                f.isAccessible = true
                f.getInt(null)
            }.getOrNull()
            if (value != null) return value
        }
        return 2
    }

    // ---------------------------------------------------------------- 取 Context

    /**
     * 只走 currentActivityThread()，拿不到就返回 false 让上层重试。
     * 绝不调用 systemMain() —— 那是把 system_server 搞死的头号凶手。
     */
    private fun ensureSystemContext(): Boolean {
        if (systemContext != null) return true
        val atClass = Class.forName("android.app.ActivityThread", false, classLoader)

        val activityThread = runCatching {
            val m = atClass.getDeclaredMethod("currentActivityThread")
            m.isAccessible = true
            m.invoke(null)
        }.getOrNull() ?: return false

        val ctx = runCatching {
            val m = atClass.getDeclaredMethod("getSystemContext")
            m.isAccessible = true
            m.invoke(activityThread) as? Context
        }.getOrNull() ?: return false

        systemContext = ctx
        powerManager = runCatching { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
        return true
    }

    // ---------------------------------------------------------------- 等待开机

    private fun waitForBootCompleted() {
        for (i in 1..BOOT_WAIT_TICKS) {
            if (bootCompleted()) {
                logi("检测到开机完成，继续初始化")
                return
            }
            sleepQuietly(BOOT_WAIT_TICK_MS)
        }
        logw("等待开机完成超时（${BOOT_WAIT_TICKS * BOOT_WAIT_TICK_MS / 1000}s），按开机完成处理")
    }

    private fun bootCompleted(): Boolean {
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            val get = spClass.getDeclaredMethod("get", String::class.java)
            get.isAccessible = true
            val a = get.invoke(null, "sys.boot_completed") as? String
            val b = get.invoke(null, "dev.bootcomplete") as? String
            a == "1" || b == "1"
        } catch (t: Throwable) {
            false
        }
    }

    /** 后门：进不了系统时，从 recovery 的 adb shell 执行 setprop 关掉本模块 */
    private fun isDisabledByProperty(): Boolean {
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            val get = spClass.getDeclaredMethod("get", String::class.java, String::class.java)
            get.isAccessible = true
            val a = get.invoke(null, "sys.autoscreenoff.disable", "") as? String
            val b = get.invoke(null, "persist.sys.autoscreenoff.disable", "") as? String
            a == "1" || b == "1"
        } catch (t: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- 基础设施

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            loge("guard 捕获异常", t)
        }
    }

    /**
     * 开线程必须走这里：装自己的 UncaughtExceptionHandler，
     * 否则一次未捕获异常就会让 system_server 退出、整机重启。
     */
    private fun safeThread(name: String, body: () -> Unit) {
        val t = thread(name = name, isDaemon = true, start = false) {
            try {
                body()
            } catch (t: Throwable) {
                loge("线程 $name 异常", t)
            }
        }
        t.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { th, e ->
            runCatching { loge("线程 ${th?.name} 未捕获异常", e) }
        }
        t.start()
    }

    private fun logi(msg: String) = log(Log.INFO, msg)
    private fun logw(msg: String) = log(Log.WARN, msg)

    private fun loge(msg: String, tr: Throwable? = null) = log(Log.ERROR, msg, tr)

    private fun log(priority: Int, msg: String, tr: Throwable? = null) {
        runCatching {
            if (tr == null) module.log(priority, Config.TAG, msg)
            else module.log(priority, Config.TAG, msg, tr)
        }
    }

    private companion object {
        /** 开机完成后的额外缓冲，避开锁屏阶段 */
        const val POST_BOOT_DELAY_MS = 20_000L

        const val BOOT_WAIT_TICK_MS = 5_000L
        const val BOOT_WAIT_TICKS = 60          // 最多等 5 分钟

        /** 开机后先快轮询，让用户打开开关后能很快生效；之后转慢轮询，一直等下去 */
        const val FAST_POLL_MS = 10_000L
        const val FAST_POLL_ROUNDS = 12          // 前 2 分钟每 10 秒一次
        const val SLOW_POLL_MS = 60_000L         // 之后每分钟一次，直到注册成功

        /** 远程配置连着读不到这么多次，就当作用户想用，直接注册 */
        const val PREF_FAILURE_FALLBACK = 5

        const val REGISTER_ATTEMPTS = 3
        const val REGISTER_RETRY_MS = 15_000L

        const val MIN_SLEEP_INTERVAL_MS = 3_000L
    }
}
