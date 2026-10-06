package me.frk2222.autoscreenoffcode.xposed

import android.content.SharedPreferences

/**
 * 模块配置。
 *
 * 这一份配置同时被三处使用：
 *  1. 目标 App 进程（读取，判断多久没操作）
 *  2. system_server 进程（读取 token，执行息屏）
 *  3. 模块自己的 UI（写入）
 *
 * 存储位置是 libxposed 的 remote preferences（group = "config"）。
 * 这里的 remote 指的是「跨进程」——由框架在本机负责同步给各个被 hook 的进程，
 * 不涉及任何网络，所以 UI 里改完立刻在 hook 侧生效，也不需要重启应用。
 */
object Config {

    const val TAG = "AutoScreenOff"
    const val PREFS_GROUP = "config"

    // ---- 广播 ----
    const val ACTION_SCREEN_OFF = "me.frk2222.autoscreenoffcode.action.SCREEN_OFF"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_FORCE = "force"

    // ---- 键名 ----
    const val KEY_ENABLED = "enabled"          // 总开关
    const val KEY_DRY_RUN = "dry_run"          // 安全模式：只记日志，不真正息屏
    const val KEY_DEBUG = "debug"              // 详细日志
    const val KEY_TOKEN = "token"              // 校验令牌，防止别人伪造广播

    /**
     * 是否让 system_server 注册息屏接收器。
     *
     * 默认关闭：hook system_server 一旦出错会导致整机反复重启。
     * 装上新版本一定不会卡开机；用户确认要用了，自己在 App 里打开这个开关。
     * 打开后 system_server 会在下一次轮询（最多 30 秒）里完成注册，无需重启。
     */
    const val KEY_SYSTEM_ENABLED = "system_enabled"
    const val DEFAULT_SYSTEM_ENABLED = false
    const val KEY_GLOBAL_VALUE = "global_value"
    const val KEY_GLOBAL_UNIT = "global_unit"

    /** 单应用覆盖的键前缀，完整键为 "app:<包名>" */
    const val PREFIX_APP = "app:"
    /** 单应用覆盖的值："<数值>|<单位key>"，或 VALUE_OFF 表示该应用不生效 */
    const val VALUE_OFF = "off"

    const val DEFAULT_VALUE = 30
    const val DEFAULT_UNIT = "m"
    const val DEFAULT_DRY_RUN = true

    /** 输入框最多 4 位数字 */
    const val MAX_VALUE = 9999L

    /**
     * 最短间隔（秒）。再短就会「息屏 → 手指一碰亮屏 → 再息屏」来回抽搐，
     * 所以无论单位是什么，算出来的总时长都不能低于这个值。
     */
    const val MIN_SECONDS = 30L

    enum class TimeUnit(val key: String, val seconds: Long, val label: String) {
        SECOND("s", 1L, "秒"),
        MINUTE("m", 60L, "分钟"),
        HOUR("h", 3600L, "小时");

        companion object {
            val LABELS: List<String> = entries.map { it.label }

            fun fromKey(key: String?): TimeUnit = entries.firstOrNull { it.key == key } ?: MINUTE

            fun indexOfKey(key: String?): Int {
                val i = entries.indexOfFirst { it.key == key }
                return if (i < 0) 1 else i
            }
        }
    }

    fun encode(value: Long, unit: TimeUnit): String = "$value|${unit.key}"

    /**
     * 某个单位下允许填的最小数值：总时长不低于 [MIN_SECONDS]，且至少是 1。
     * 秒 -> 30，分 -> 1（60 秒），时 -> 1。
     */
    fun minValue(unit: TimeUnit): Long =
        maxOf(1L, (MIN_SECONDS + unit.seconds - 1) / unit.seconds)

    /** 把数值钳到合法区间：[minValue] .. [MAX_VALUE] */
    fun clampValue(value: Long, unit: TimeUnit): Long =
        value.coerceIn(minValue(unit), MAX_VALUE)

    /** 把「数值 + 单位」换成秒，并保证不低于 [MIN_SECONDS]（兜住历史遗留的非法值） */
    fun toSeconds(value: Long, unit: TimeUnit): Long =
        (value * unit.seconds).coerceAtLeast(MIN_SECONDS)

    /** 读取某个包的原始覆盖值：null=跟随全局，"off"=不生效，"30|m"=自定义 */
    fun rawOverride(prefs: SharedPreferences, pkg: String): String? =
        prefs.getString(PREFIX_APP + pkg, null)

    /**
     * 计算某个包的超时时间（毫秒）。
     * 返回值 <= 0 表示该包不启用息屏。
     */
    fun timeoutMs(prefs: SharedPreferences, pkg: String): Long {
        val raw = rawOverride(prefs, pkg)
        if (!raw.isNullOrEmpty()) {
            if (raw == VALUE_OFF) return -1L
            val idx = raw.indexOf('|')
            if (idx > 0) {
                val value = raw.substring(0, idx).toLongOrNull() ?: return -1L
                val unit = TimeUnit.fromKey(raw.substring(idx + 1))
                if (value <= 0L) return -1L
                return toSeconds(value, unit) * 1000L
            }
        }
        val value = prefs.getInt(KEY_GLOBAL_VALUE, DEFAULT_VALUE).toLong()
        if (value <= 0L) return -1L
        val unit = TimeUnit.fromKey(prefs.getString(KEY_GLOBAL_UNIT, DEFAULT_UNIT))
        return toSeconds(value, unit) * 1000L
    }

    /** 全局默认时长，格式化成人话，给 UI 显示用 */
    fun globalText(prefs: SharedPreferences): String {
        val value = prefs.getInt(KEY_GLOBAL_VALUE, DEFAULT_VALUE)
        val unit = TimeUnit.fromKey(prefs.getString(KEY_GLOBAL_UNIT, DEFAULT_UNIT))
        return "$value ${unit.label}"
    }

    /** 某个包当前生效的时长，格式化成人话，给 UI 显示用 */
    fun effectiveText(prefs: SharedPreferences, pkg: String): String {
        val raw = rawOverride(prefs, pkg)
        if (raw == VALUE_OFF) return "已关闭"
        if (!raw.isNullOrEmpty()) {
            val idx = raw.indexOf('|')
            if (idx > 0) {
                val value = raw.substring(0, idx)
                val unit = TimeUnit.fromKey(raw.substring(idx + 1))
                return "单独设置：$value ${unit.label}"
            }
        }
        return "跟随全局（${globalText(prefs)}）"
    }
}
