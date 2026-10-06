package me.frk2222.autoscreenoffcode.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标的显示 / 隐藏。
 *
 * 原理：桌面入口不是 MainActivity 本身，而是 manifest 里的 `activity-alias`（.LauncherAlias）。
 * 把 alias 置为 DISABLED，桌面就查不到带 LAUNCHER 的入口，图标消失；
 * 而 MainActivity 始终 enabled，且声明了 Xposed 惯例的
 * `de.robv.android.xposed.category.MODULE_SETTINGS`，
 * LSPosed 的「启动」菜单正是靠这个 category 显式启动我们的，所以隐藏图标后依然进得来。
 *
 * 注意必须带 DONT_KILL_APP：否则系统会杀掉当前进程，用户点完开关 App 直接闪退。
 */
object LauncherIcon {

    /** manifest 中 activity-alias 的完整类名（包名 + .LauncherAlias） */
    private fun aliasName(context: Context) = "${context.packageName}.LauncherAlias"

    fun isHidden(context: Context): Boolean {
        return runCatching {
            val state = context.packageManager
                .getComponentEnabledSetting(ComponentName(context.packageName, aliasName(context)))
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    /** 返回是否设置成功 */
    fun setHidden(context: Context, hidden: Boolean): Boolean {
        return runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, aliasName(context)),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.getOrDefault(false)
    }
}
