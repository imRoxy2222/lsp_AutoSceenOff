package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.frk2222.autoscreenoffcode.ui.theme.AppSemantic
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * 关于页。
 *
 * ★ 项目地址写在下面的 [GITHUB_URL]，下面 [LINKS] 里的条目都从它派生，改这一处即可。
 */
private const val GITHUB_URL = "https://github.com/imRoxy2222/lsp_AutoSceenOff"

/** 大卡的宽高比。用比例而不是写死 dp，屏幕宽窄都能保持同一个形状 */
private const val HERO_ASPECT = 1.32f

/** 大卡的圆角，比普通卡片再大一点，撑得住面积 */
private val HERO_CORNER = 22.dp

/** 大卡里的内容留白 */
private val HERO_PADDING = PaddingValues(horizontal = 20.dp, vertical = 18.dp)

/** 流光扫一整趟的时长。太快手感浮躁，太慢就看不出在动 */
private const val FLOW_MILLIS = 5200

private data class LinkItem(val title: String, val subtitle: String, val url: String)

/**
 * 大卡下方的引用链接。要加新链接就往这个 list 里补一行，排版自动跟上。
 */
private val LINKS = listOf(
    LinkItem("GitHub", GITHUB_URL.removePrefix("https://"), GITHUB_URL),
    LinkItem("反馈问题", "提 bug、提需求都走这里", "$GITHUB_URL/issues"),
)

@Composable
fun AboutScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val version = remember(context) { selfVersionName(context) }
    var openFailed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(screenSpacing),
    ) {
        // ---------------- 顶部大卡 ----------------
        item {
            HeroCard(
                version = version,
                context = context,
            )
        }

        // ---------------- 引用链接 ----------------
        item { SmallTitle(text = "相关链接") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LINKS.forEachIndexed { index, link ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(dividerPadding))
                    }
                    key(link.url) {
                        ArrowPreference(
                            title = link.title,
                            summary = link.subtitle,
                            onClick = { openFailed = !openUrl(context, link.url) },
                        )
                    }
                }
            }
        }

        if (openFailed) {
            item {
                NoticeCard(
                    notice = Notice("没有可用的浏览器，请手动打开：$GITHUB_URL", NoticeTone.ERROR),
                )
            }
        }
    }
}

/**
 * 顶部那张带流光的大卡。
 *
 * 尺寸用 [Modifier.aspectRatio] 按宽度算高度：写死 400dp 这类数值在小屏上会被挤爆、
 * 在大屏上又缩成一小块，按比例才是一张「跟着屏幕走」的卡。
 */
@Composable
private fun HeroCard(
    version: String,
    context: Context,
) {
    val colors = if (isSystemInDarkTheme()) AppSemantic.darkHero else AppSemantic.lightHero
    val selfPkg = context.packageName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT)
            .squircleClip(HERO_CORNER)
            .flowingLight(colors)
            .padding(HERO_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg = selfPkg, label = "无操作息屏", size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "无操作息屏",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "autoScreenOff",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "按「无操作时长」强制息屏，不看是否在播放视频。老人刷短视频睡着后，手机不会再亮一整夜。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color.White.copy(alpha = 0.88f),
        )

        // 撑开中间，把版本信息压到卡片底部，和上面那块拉开距离
        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(
                text = "版本 $version",
                color = Color.White,
                containerColor = Color.White.copy(alpha = 0.18f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(
                text = "libxposed API 102",
                color = Color.White,
                containerColor = Color.White.copy(alpha = 0.18f),
            )
        }
    }
}

/**
 * 流光底：先铺一层斜向渐变，再让一条半透明的高光带斜着循环扫过去。
 *
 * 两个细节：
 * - 进度在 **绘制阶段** 读（[State.getValue] 写在 drawBehind 里），
 *   所以每帧只重绘，不会每帧重组整张卡；
 * - 高光带要走完「完全在视口外 → 完全离开视口」的全程再回头，
 *   否则会在两端突然闪一下。
 */
@Composable
private fun Modifier.flowingLight(colors: List<Color>): Modifier {
    val transition = rememberInfiniteTransition(label = "流光")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FLOW_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "流光进度",
    )
    return this.drawBehind {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )

        // 斜着扫：带子横向宽度取 w 的 45%，从左上角外侧一路走到右下角外侧
        val band = w * 0.45f
        val head = -band + progress.value * (w + band)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                start = Offset(head - band, 0f),
                end = Offset(head + band, h),
            ),
        )
    }
}

private fun openUrl(context: Context, url: String): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    true
}.getOrDefault(false)
