package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.frk2222.autoscreenoffcode.ui.theme.AppSemantic
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 各页面共用的小件。统一在这里，免得每处样式各写一套。 */

/** 正常 / 提醒 / 异常三档语义色，比直接写死 RED / GREEN 在深浅色下都更稳 */
@Composable
internal fun toneOk(): Color =
    if (isSystemInDarkTheme()) AppSemantic.darkOk else AppSemantic.lightOk

@Composable
internal fun toneWarn(): Color =
    if (isSystemInDarkTheme()) AppSemantic.darkWarn else AppSemantic.lightWarn

@Composable
internal fun toneError(): Color =
    if (isSystemInDarkTheme()) AppSemantic.darkError else AppSemantic.lightError

/** 中性色：次要信息用 */
@Composable
internal fun toneMuted(): Color = MiuixTheme.colorScheme.onSurfaceVariantSummary

@Composable
internal fun tonePrimary(): Color = MiuixTheme.colorScheme.primary

/** 状态圆点 */
@Composable
internal fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** 「标签 —— 值」一行。用于状态卡里的信息清单 */
@Composable
internal fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 说明文字：比正文小、比 Color 淡 */
@Composable
internal fun Hint(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    Text(
        modifier = modifier,
        text = text,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = color,
    )
}

/** 包名 -> 应用名。取不到就退回包名 */
internal fun appLabel(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (t: Throwable) {
    pkg
}

/** 本 App 的版本名 / 版本号，给「关于」页显示。取不到返回占位符 */
internal fun selfVersionName(context: Context): String = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrDefault("-") ?: "-"

/** 目标应用是否还在作用域里（已卸载时不至于报错） */
internal fun isPackageInstalled(context: Context, pkg: String): Boolean = runCatching {
    context.packageManager.getApplicationInfo(pkg, 0)
    true
}.getOrDefault(false)

/**
 * 「回到顶部」箭头。
 *
 * 自己画而不用 MIUIX 内置图标：它的 ExpandLess / Back 是出版社自绘路径，
 * 方向要靠猜，转 90 度容易翻车；这一个从左上到右下怎么用都是朝上，行为确定。
 */
internal fun rememberArrowUp(): ImageVector = ImageVector.Builder(
    name = "ArrowUp",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color(0xFF000000)),
        fillAlpha = 1f,
    ) {
        moveTo(12f, 5.2f)
        lineTo(20.4f, 13.6f)
        lineTo(18.5f, 15.5f)
        lineTo(12f, 9f)
        lineTo(5.5f, 15.5f)
        lineTo(3.6f, 13.6f)
        close()
    }
}.build()
