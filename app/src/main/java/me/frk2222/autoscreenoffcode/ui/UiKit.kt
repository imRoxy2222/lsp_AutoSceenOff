package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 各页面共用的小件。统一在这里，免得每处样式各写一套。 */

/**
 * 装普通内容的 [Card] 统一内边距。
 *
 * ★ MIUIX 的 Card 默认 `insideMargin = PaddingValues(0.dp)`，往里直接丢文字会贴着圆角，
 * 圆角看起来就是「坏了」。16dp 是特意选的——和 SwitchPreference / ArrowPreference
 * 自带的 16dp 一致，两种卡片的文字才在同一条竖线上。
 *
 * 装 preference 组件的 Card **不要**传它，否则变成 16+16 双重缩进。
 */
internal val cardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/** 装 preference 的 Card 里，两条之间的分割线：那张卡没有内边距，得自己缩进到和文字对齐 */
internal val dividerPadding = PaddingValues(horizontal = 16.dp)

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

/**
 * 语义色 -> 同色系淡底。
 *
 * 不能直接 `color.copy(alpha = 0.2f)`：那样会透出下层卡片颜色，深浅色下深浅不一。
 * 把语义色按很小比例混进卡片底色，得到一张「有颜色但不刺眼」的底，用来做徽章 / 提示卡。
 */
@Composable
internal fun tintOf(color: Color, fraction: Float = 0.14f): Color {
    val base = MiuixTheme.colorScheme.surfaceContainer
    return Color(
        red = base.red + (color.red - base.red) * fraction,
        green = base.green + (color.green - base.green) * fraction,
        blue = base.blue + (color.blue - base.blue) * fraction,
    )
}

/** 状态圆点 */
@Composable
internal fun StatusDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * 小徽章（圆角胶囊 + 淡色底 + 同色文字）。
 *
 * 像「计时中 / 已停用 / 支持 / 不支持」这种只有两三个字的状态词，
 * 光靠文字颜色区分会显得散，给它一个自己的底色块才看得住。
 */
@Composable
internal fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tintOf(color))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/**
 * 操作结果的语气。决定提示条底色，避免到处临时判断「这句该是什么颜色」。
 * INFO = 单纯的进行中 / 中性说明，用主色。
 */
internal enum class NoticeTone {
    OK, WARN, ERROR, INFO,
}

/** 一次操作的结果：说了什么 + 用什么语气 */
internal data class Notice(val text: String, val tone: NoticeTone)

@Composable
internal fun NoticeTone.toneColor(): Color = when (this) {
    NoticeTone.OK -> toneOk()
    NoticeTone.WARN -> toneWarn()
    NoticeTone.ERROR -> toneError()
    NoticeTone.INFO -> tonePrimary()
}

/**
 * 框架回调（申请 / 移除作用域）的一句话 -> 提示条。
 * 那些回调只给字符串，成功文案都以「已」开头（「已加入…」「已把…移出…」），
 * 其余按失败处理，这样不用把每条文案的语气记在各页面里。
 */
internal fun scopeNotice(msg: String): Notice =
    Notice(msg, if (msg.startsWith("已")) NoticeTone.OK else NoticeTone.ERROR)

/**
 * 一整块提示条：圆点 + 说明，底色跟着语义走。
 * 给操作结果用，比一句灰色小字更容易被看见。
 */
@Composable
internal fun NoticeCard(
    notice: Notice,
    modifier: Modifier = Modifier,
) {
    val color = notice.tone.toneColor()
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = tintOf(color, 0.12f)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(
                color = color,
                size = 8.dp,
                modifier = Modifier.padding(top = 5.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = notice.text,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
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
            .padding(vertical = 6.dp),
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
