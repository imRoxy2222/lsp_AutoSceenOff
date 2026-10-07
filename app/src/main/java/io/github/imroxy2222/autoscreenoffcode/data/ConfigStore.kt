package io.github.imroxy2222.autoscreenoffcode.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.imroxy2222.autoscreenoffcode.xposed.Config
import java.util.*

/**
 * UI 侧对跨进程配置（libxposed 的 remote preferences）的读写封装。
 *
 * 这份 prefs 由框架在本机做跨进程同步，写入后 hook 侧下一次读取就能看到，不涉及网络。
 * 每写一次就自增 revision，Compose 通过它触发重组。
 */
object ConfigStore {

    var revision by mutableStateOf(0)
        private set

    /** 拿到跨进程配置本体；模块未激活时返回 null */
    fun snapshot(): SharedPreferences? = prefs()

    private fun prefs(): SharedPreferences? =
        runCatching { Framework.service?.getRemotePreferences(Config.PREFS_GROUP) }.getOrNull()

    fun int(key: String, def: Int): Int =
        runCatching { prefs()?.getInt(key, def) ?: def }.getOrDefault(def)

    fun bool(key: String, def: Boolean): Boolean =
        runCatching { prefs()?.getBoolean(key, def) ?: def }.getOrDefault(def)

    fun string(key: String, def: String): String =
        runCatching { prefs()?.getString(key, def) ?: def }.getOrDefault(def)

    /** 写入。返回 false 表示没写进去（通常是模块未激活、服务没绑上） */
    fun write(block: SharedPreferences.Editor.() -> Unit): Boolean {
        val p = prefs() ?: return false
        val ok = runCatching {
            p.edit().apply {
                block()
                apply()
            }
        }.isSuccess
        revision++
        return ok
    }

    fun remove(key: String): Boolean = write { remove(key) }

    fun put(key: String, value: Int): Boolean = write { putInt(key, value) }
    fun put(key: String, value: Boolean): Boolean = write { putBoolean(key, value) }
    fun put(key: String, value: String): Boolean = write { putString(key, value) }

    /** 生成并保存一次性令牌；已有则直接返回 */
    fun ensureToken(): String {
        val existing = string(Config.KEY_TOKEN, "")
        if (existing.isNotEmpty()) return existing
        val token = UUID.randomUUID().toString().replace("-", "")
        put(Config.KEY_TOKEN, token)
        return token
    }
}
