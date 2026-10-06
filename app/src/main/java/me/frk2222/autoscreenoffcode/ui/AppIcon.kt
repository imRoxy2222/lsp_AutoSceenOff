package me.frk2222.autoscreenoffcode.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 应用图标读取

/** 解码尺寸（dp）：列表里实际显示 36dp，多留一点余量，避免高 DPI 屏上放大发虚 */
private val ICON_DECODE_SIZE = 40.dp

/** 图标缓存上限 4MB。手机上几百个应用，全部常驻内存要几十 MB，必须淘汰 */
private const val ICON_CACHE_BYTES = 4 * 1024 * 1024

private val iconLoader: ExecutorService by lazy {
    Executors.newFixedThreadPool(3) { runnable ->
        Thread(runnable, "app-icon").apply { isDaemon = true }
    }
}

/**
 * 包名 -> 已解码图标。
 *
 * 用 LruCache 而不是无限 Map：Decode 出来的 Bitmap 是按 density 算的真实像素，
 * 高 DPI 屏上一张就几十 KB，几百张不放限制了会长到几十 MB。
 * LruCache 本身方法带 synchronized，worker 线程和主线程都能直接读写。
 */
private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

private val iconShape = RoundedCornerShape(8.dp)

private val appIconMainHandler = Handler(Looper.getMainLooper())

/**
 * 在后台线程取出应用图标并缩放到统一尺寸。
 *
 * 走 [android.content.pm.PackageManager.getApplicationIcon] 而不是 Activity 的 icon，
 * 因为作用域里很多目标（以及系统组件）压根没有 launcher activity。
 * 自适应图标（AdaptiveIconDrawable）和普通 BitmapDrawable 都能直接 draw 到画布上。
 */
private fun loadAppIcon(context: Context, pkg: String): Bitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(pkg)
    val px = (ICON_DECODE_SIZE.value * context.resources.displayMetrics.density)
        .roundToInt().coerceAtLeast(48)

    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val oldBounds = drawable.bounds
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bitmap))
    drawable.bounds = oldBounds
    bitmap
}.getOrNull()

/**
 * 应用图标。
 *
 * 只在**当前可见**的列表项里加载：LazyColumn 是懒布局，滚出屏幕后 [DisposableEffect]
 * 被 dispose，标记失效后即使 worker 线程跑完也不会再触发重组；滚回来时先命中缓存直接显示。
 * 这样几百个应用也不会一次性全部解码导致卡顿。
 */
@Composable
internal fun AppIcon(
    pkg: String,
    label: String = "",
    size: Dp = 36.dp,
) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(iconCache.get(pkg)?.asImageBitmap()) }

    if (icon == null) {
        DisposableEffect(pkg) {
            var alive = true
            iconLoader.execute {
                val bitmap = iconCache.get(pkg)
                    ?: loadAppIcon(context, pkg)?.also { iconCache.put(pkg, it) }
                val image = bitmap?.asImageBitmap()
                if (image != null) {
                    appIconMainHandler.post { if (alive) icon = image }
                }
            }
            onDispose { alive = false }
        }
    }

    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(iconShape),
        )
    } else {
        // 占位：保证加载完成后文字和勾选框不会左右跳动
        AppIconPlaceholder(label, size)
    }
}

/**
 * 还没解码完、或这个包压根没图标（有些纯后台组件）时的占位块。
 * 有名字就取第一个字符，比一块空白更容易认。
 */
@Composable
private fun AppIconPlaceholder(label: String, size: Dp) {
    val tint = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(iconShape)
            .background(tint.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val letter = label.trim().firstOrNull()
        if (letter != null) {
            Text(
                text = letter.uppercaseChar().toString(),
                fontSize = (size.value / 2).sp,
                fontWeight = FontWeight.Medium,
                color = tint.copy(alpha = 0.45f),
            )
        }
    }
}
