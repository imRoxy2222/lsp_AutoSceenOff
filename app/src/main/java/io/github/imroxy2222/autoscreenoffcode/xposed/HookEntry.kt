package io.github.imroxy2222.autoscreenoffcode.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.*

/**
 * 模块入口。整个模块只有这一个入口类（libxposed 要求单入口才支持热重载）。
 *
 * 它会同时被注入到：
 *  - system_server：负责真正执行息屏
 *  - 被勾选的 App：负责记录「最后一次操作」并在超时后发广播
 */
class HookEntry : XposedModule() {

    private var isSystemServer = false
    private var monitor: AppMonitor? = null
    private var screenOff: ScreenOffService? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        isSystemServer = param.isSystemServer
        logd(
            "模块载入：process=${param.processName}, systemServer=$isSystemServer, " +
                "framework=${frameworkName} ${frameworkVersion}, api=${apiVersion}"
        )
        if (isSystemServer && frameworkProperties and PROP_CAP_SYSTEM == 0L) {
            logw("当前框架缺少 PROP_CAP_SYSTEM 能力位，息屏功能不可用")
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        logd("system server 启动，准备注册息屏广播接收器")
        runCatching {
            val service = ScreenOffService(this, param.classLoader)
            screenOff = service
            service.start()
        }.onFailure { loge("初始化息屏服务失败", it) }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (isSystemServer) return
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        if (pkg.isEmpty() || pkg == "android" || pkg == "system") return
        if (pkg == moduleApplicationInfo.packageName) return
        runCatching {
            val m = AppMonitor(this, pkg, param.classLoader)
            monitor = m
            m.install()
        }.onFailure { loge("[$pkg] 安装无操作检测失败", it) }
    }

    private fun logd(msg: String) {
        log(Log.DEBUG, Config.TAG, msg)
    }

    private fun logw(msg: String) {
        log(Log.WARN, Config.TAG, msg)
    }

    private fun loge(msg: String, tr: Throwable? = null) {
        if (tr == null) log(Log.ERROR, Config.TAG, msg)
        else log(Log.ERROR, Config.TAG, msg, tr)
    }
}
