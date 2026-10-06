package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.frk2222.autoscreenoffcode.ui.theme.AppSemantic
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    /** 默认是由语义色算出的淡底；放在深色底（比如关于页那张大卡）上时要自己给 */
    containerColor: Color = tintOf(color),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
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

/**
 * 一排里用的小胶囊按钮：选中的那个主色填充，其余灰底，一眼看出当前是哪个。
 *
 * 一行最多放 4 个（MIUIX 的 Button 有 58dp 最小宽度，再挤就压字了），
 * 超出就换行或改成二级页面。外面用 `Modifier.weight(1f)` 平分宽度。
 */
@Composable
internal fun ChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = if (selected) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}

/**
 * 数字输入框：只收数字，带上限 / 下限，光标位置正常。
 *
 * ★ 光标的坑：以前是 `TextFieldValue(text)` 现拼一个值传进去 —— [TextFieldValue] 不带 selection
 * 时光标默认为 0（最前面），于是每敲一个字符光标就被拽回开头，退格键删不掉东西。
 * 这里改成自己持有完整的 [TextFieldValue]（文本 + selection 一起存），
 * 过滤字符时把光标按「删掉了几个字」同步挪位，光标就待在你输的地方。
 *
 * 下限不在打字时硬改文本 —— 想输 300 得先敲 3，一敲就被抬成 30 的话永远输不进去。
 * 做法是：打字时照写，但**写进配置的一定是钳过的合法值**；等失焦（或点键盘上的完成）
 * 再把输入框里的文本收敛成真正生效的那个数。
 *
 * @param value 当前已提交的值（外部权威值，比如从配置里读出来的）
 * @param onValueChange 改动回调，传出来的值一定在 [min]..[max] 之间
 * @param min 允许的最小值，由调用方按单位算好（总时长不能短于 30 秒）
 */
@Composable
internal fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    modifier: Modifier = Modifier,
    max: Int = 9999,
    label: String = "数值",
    enabled: Boolean = true,
) {
    var field by remember { mutableStateOf(TextFieldValue(value.toString())) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    // 失焦 / 外部值变了 → 把文本收敛成合法值：空、小于下限、超过上限都会被拉回来。
    // 顺手把光标挪到末尾，下次接着输入不会插在数字中间。
    // 顺带还兼了「升级后旧配置不合法」的自愈：进页面就会把 5 秒这种历史值抬成 30 秒。
    LaunchedEffect(value, min, max, focused) {
        if (!focused) {
            val parsed = field.text.toIntOrNull()
            val fixed = parsed?.coerceIn(min, max) ?: value
            if (parsed != fixed || field.text != fixed.toString()) {
                val text = fixed.toString()
                field = TextFieldValue(text, TextRange(text.length))
                if (fixed != value) onValueChange(fixed)
            }
        }
    }

    TextField(
        value = field,
        onValueChange = { next ->
            val filtered = next.digitsOnly(max)
            field = filtered
            val parsed = filtered.text.toIntOrNull() ?: return@TextField
            onValueChange(parsed.coerceIn(min, max))
        },
        label = label,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        // 点「完成」就清焦点，剩下的收敛逻辑由上面的失焦分支接管
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

/**
 * 只留数字（最多 [max] 位），光标跟着一起挪。
 *
 * 逐字扫描时记下「原下标 -> 新下标」的映射，再把 selection 的两端映射过去，
 * 这样删掉的是哪个位置的字符，光标就停在哪个位置，不会跳到开头。
 */
private fun TextFieldValue.digitsOnly(max: Int): TextFieldValue {
    val src = text
    val out = StringBuilder()
    val map = IntArray(src.length + 1)
    for (i in src.indices) {
        map[i] = out.length
        val c = src[i]
        if (out.length < max && c.isDigit()) out.append(c)
    }
    map[src.length] = out.length
    val start = map[selection.start.coerceIn(0, src.length)]
    val end = map[selection.end.coerceIn(0, src.length)]
    return TextFieldValue(out.toString(), TextRange(start, end))
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
