package me.frk2222.autoscreenoffcode.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 本模块自己的配色。
 *
 * 思路：页面底色（surface）用带一点点冷调的浅灰，卡片（surfaceContainer）保持纯白，
 * 这样「页面 - 卡片」有清晰层次，不用靠描边；强调色沿用 MIUIX 蓝并略微调深。
 *
 * 关键点：MIUIX 里 Scaffold / TopAppBar / NavigationBar 的默认背景都取 [Colors.surface]，
 * 而 [Card] 默认取 [Colors.surfaceContainer]，所以改这两个就能整体换肤，
 * 其余颜色保持默认，避免把某个组件弄坏。
 */

private val LightPrimary = Color(0xFF2A6DF4)
private val DarkPrimary = Color(0xFF5B9BFF)

/** 语义色：正常 / 提醒 / 异常。比直接用 GREEN / RED 更容易在深浅色下看清 */
object AppSemantic {
    val lightOk = Color(0xFF2E9E5B)
    val darkOk = Color(0xFF4DCB7A)
    val lightWarn = Color(0xFFE0952B)
    val darkWarn = Color(0xFFFFB951)
    val lightError = Color(0xFFE0443A)
    val darkError = Color(0xFFF97A70)
}

private fun lightAppColors(): Colors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryVariant = LightPrimary,
    onPrimaryVariant = Color(0xFFBAD3FF),
    disabledPrimary = Color(0xFFBACDF2),
    disabledPrimaryButton = Color(0xFFBACDF2),
    disabledPrimarySlider = Color(0xFFB3C6EC),
    primaryContainer = Color(0xFF4C88F5),
    tertiaryContainer = Color(0xFFE8F1FF),
    onTertiaryContainer = LightPrimary,
    secondary = Color(0xFFE8ECF4),
    secondaryVariant = Color(0xFFF1F4FA),
    onSecondaryVariant = Color(0xFF2B303B),
    secondaryContainer = Color(0xFFF1F4FA),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF101318),
    onBackgroundVariant = Color(0xFF8A90A0),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF101318),
    onSurfaceSecondary = Color(0xCC101318),
    onSurfaceVariantSummary = Color(0x99101318),
    onSurfaceVariantActions = Color(0x66101318),
    disabledOnSurface = Color(0xFFA9AFBD),
    surfaceContainer = Color.White,
    onSurfaceContainer = Color(0xFF101318),
    onSurfaceContainerVariant = Color(0xFF8E94A3),
    surfaceContainerHigh = Color(0xFFE9ECF4),
    onSurfaceContainerHigh = Color(0xFF8E94A3),
    surfaceContainerHighest = Color(0xFFE3E7F0),
    onSurfaceContainerHighest = Color(0xFF101318),
    outline = Color(0xFFDBE0EA),
    dividerLine = Color(0xFFE7EAF1),
)

private fun darkAppColors(): Colors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0B1220),
    primaryVariant = Color(0xFF4C90FF),
    onPrimaryVariant = Color(0xFF9EC7FF),
    disabledPrimary = Color(0xFF22365A),
    disabledOnPrimary = Color(0xFF5C6D87),
    disabledPrimaryButton = Color(0xFF22365A),
    disabledOnPrimaryButton = Color(0xFF5C6D87),
    disabledPrimarySlider = Color(0xFF2F4574),
    primaryContainer = Color(0xFF3D7DE8),
    tertiaryContainer = Color(0xFF1B2A45),
    onTertiaryContainer = DarkPrimary,
    secondary = Color(0xFF4A5060),
    secondaryVariant = Color(0xFF3A3F4C),
    onSecondaryVariant = Color(0xFFE4E7EE),
    secondaryContainer = Color(0xFF3A3F4C),
    background = Color(0xFF0E1015),
    onBackground = Color(0xE6FFFFFF),
    onBackgroundVariant = Color(0xFF7A8091),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceSecondary = Color(0xCCFFFFFF),
    onSurfaceVariantSummary = Color(0x99FFFFFF),
    onSurfaceVariantActions = Color(0x66FFFFFF),
    disabledOnSurface = Color(0xFF5A5F6B),
    surfaceContainer = Color(0xFF171921),
    onSurfaceContainer = Color(0xE6FFFFFF),
    onSurfaceContainerVariant = Color(0xFF8A90A0),
    surfaceContainerHigh = Color(0xFF1E212B),
    onSurfaceContainerHigh = Color(0xFF8A90A0),
    surfaceContainerHighest = Color(0xFF242833),
    onSurfaceContainerHighest = Color(0xFFE9ECF2),
    outline = Color(0xFF2B303B),
    dividerLine = Color(0xFF22262F),
)

/**
 * 应用主题。跟随系统深浅色，用上面这套自定义色板。
 *
 * 注意 [ThemeController] 必须 remember：每次重组都新建一个会让渐变色的 remember 缓存失效。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            lightColors = lightAppColors(),
            darkColors = darkAppColors(),
        )
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
