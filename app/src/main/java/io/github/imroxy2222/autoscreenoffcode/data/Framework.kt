package io.github.imroxy2222.autoscreenoffcode.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

data class FrameworkInfo(
    /** 框架是否还活着（null 表示还没收到框架回调） */
    val name: String = "",
    val version: String = "",
    val apiVersion: Int = 0,
    val properties: Long = 0L,
    val scope: List<String> = emptyList(),
    val runningTargets: Int = 0,
    val message: String? = null,
) {
    val hasSystemCapability: Boolean
        get() = properties and XposedService.PROP_CAP_SYSTEM != 0L

    /** 框架是否支持跨进程配置（remote preferences）。remote 指跨进程，不是联网。 */
    val hasRemoteCapability: Boolean
        get() = properties and XposedService.PROP_CAP_REMOTE != 0L
}

object Framework {

    var service: XposedService? by mutableStateOf(null)
        private set

    var info: FrameworkInfo by mutableStateOf(FrameworkInfo())
        private set

    /** 是否已经拿到框架服务（模块被激活） */
    val activated: Boolean get() = service != null

    private var bound = false

    fun bind() {
        if (bound) return
        bound = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                val scope = runCatching { service.getScope() }.getOrElse { emptyList() }
                val targets = runCatching { service.getRunningTargets().size }.getOrDefault(0)
                this@Framework.service = service
                info = FrameworkInfo(
                    name = runCatching { service.frameworkName }.getOrDefault(""),
                    version = runCatching { service.frameworkVersion }.getOrDefault(""),
                    apiVersion = runCatching { service.apiVersion }.getOrDefault(0),
                    properties = runCatching { service.frameworkProperties }.getOrDefault(0L),
                    scope = scope,
                    runningTargets = targets,
                )
            }

            override fun onServiceDied(service: XposedService) {
                this@Framework.service = null
                info = FrameworkInfo(message = "框架服务已断开")
            }
        })
    }

    fun refresh() {
        val s = service ?: return
        info = info.copy(
            scope = runCatching { s.getScope() }.getOrDefault(info.scope),
            runningTargets = runCatching { s.getRunningTargets().size }.getOrDefault(0),
        )
    }
}
