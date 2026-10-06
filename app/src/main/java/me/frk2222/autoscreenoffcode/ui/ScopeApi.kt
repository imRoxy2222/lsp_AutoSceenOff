package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import me.frk2222.autoscreenoffcode.data.Framework

/** LSPosed 作用域改动相关的共用代码。 */

private val mainHandler = Handler(Looper.getMainLooper())

/** 框架回调在 binder 线程，必须切回主线程才能改 Compose 状态 */
internal fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}

/**
 * 向框架申请把应用加入作用域。
 *
 * 流程：勾选 → service.requestScope() → LSPosed 弹确认框 → 用户同意 →
 * 回调 onScopeRequestApproved → 框架把该应用勾进本模块作用域。
 * 注意：申请成功后目标 App 需要重启（强行停止）才会被注入。
 */
internal fun requestScope(context: Context, pkgs: List<String>, onResult: (String) -> Unit) {
    val service = Framework.service
    if (service == null) {
        onResult("模块未激活，无法申请。请先在 LSPosed 里启用本模块。")
        return
    }
    runCatching {
        service.requestScope(pkgs, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                runOnMain {
                    Framework.refresh()
                    onResult(
                        "已加入作用域 ${approved.size} 个：${approved.joinToString()}。" +
                            "请强行停止（或重启）这些应用，模块才会注入生效。",
                    )
                }
            }

            override fun onScopeRequestFailed(message: String) {
                runOnMain { onResult("申请失败：$message") }
            }
        })
    }.onFailure { runOnMain { onResult("请求异常：${it.message}") } }
}

/** 反向操作：把应用从本模块作用域里移除，之后它不再受本模块管理 */
internal fun removeScope(context: Context, pkg: String, onResult: (String) -> Unit) {
    val service = Framework.service
    if (service == null) {
        onResult("模块未激活，无法移除。")
        return
    }
    runCatching { service.removeScope(listOf(pkg)) }
        .onSuccess {
            runOnMain {
                Framework.refresh()
                onResult("已把 ${appLabel(context, pkg)} 移出作用域。")
            }
        }
        .onFailure { runOnMain { onResult("移除失败：${it.message}") } }
}
