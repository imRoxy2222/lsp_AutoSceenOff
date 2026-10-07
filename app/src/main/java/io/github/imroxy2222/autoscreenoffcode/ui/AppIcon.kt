package io.github.imroxy2222.autoscreenoffcode.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 应用图标读取

/**
 * 解码尺寸（dp）：按最大的显示尺寸（详情页 44dp）解，列表里 36dp 是缩小显示，不会发虚。
 * 再大只是白占内存 —— 缓存里能放下几个图标，直接决定滑动时要不要重新解码。
 */
private val ICON_DECODE_SIZE = 44.dp

/**
 * 图标缓存上限 12MB。
 *
 * 按 44dp / 3x 屏算一张约 83KB，12MB 能放一百多个，一般手机上「用户应用」那几十个
 * 可以整个装下 —— 滑动时全是缓存命中，就没有「先占位后补图」的异步重排了。
 * 原来 4MB 只放得下几十个，来回滑一次淘汰一次，等于每次都重新解码，是卡顿的主因之一。
 */
private const val ICON_CACHE_BYTES = 12 * 1024 * 1024

/** 解码线程数。2 条足够，再多只会和主线程抢 CPU */
private const val ICON_WORKERS = 2

/**
 * 按需队列上限 —— ★ 滑动卡顿的关键在这里。
 *
 * 快速下滑时每划过一项都会请求一次解码，无界队列会一次堆上几百个任务
 * （每一个都要跨进程问 PackageManager、再解码 APK 里的图），后台一直忙、
 * 主线程一直被通知重组，滑动就是这么卡住的。
 * 有界 + 丢最老的任务，队列里留下的永远是**最后**请求的那些，也就是屏幕上真正看得见的几十个。
 */
private const val ICON_QUEUE_CAPACITY = 48

/** 预热最多解多少个。切到「全部」时可能有几百个应用，全解一遍要好几秒还会把缓存挤爆 */
private const val ICON_PRELOAD_LIMIT = 200

/** 包名 -> 已解码图标。LruCache 自身方法带 synchronized，worker 和主线程都能直接读写 */
private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/** 已经确认「这个包没有图标」的名单。否则每划过一次这种包就白解码一次 */
private val iconMissing = HashSet<String>()

/** 同时护住 [iconCache] 的复合操作和 [iconMissing] */
private val iconLock = Any()

private val iconShape = RoundedCornerShape(8.dp)

private val appIconMainHandler = Handler(Looper.getMainLooper())

private fun iconThread(runnable: Runnable, name: String) = Thread(runnable, name).apply {
    isDaemon = true
    priority = Thread.NORM_PRIORITY - 1 // 让路给主线程
}

/** 按需解码：只服务当前看得见的那几十项 */
private val iconExecutor = ThreadPoolExecutor(
    ICON_WORKERS, ICON_WORKERS,
    30L, TimeUnit.SECONDS,
    LinkedBlockingQueue(ICON_QUEUE_CAPACITY),
    { runnable -> iconThread(runnable, "app-icon") },
    ThreadPoolExecutor.DiscardOldestPolicy(),
)

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

/** 解码一个包的图标并放进缓存。没图标的包记进黑名单，下次直接跳过 */
private fun decodeIntoCache(context: Context, pkg: String) {
    synchronized(iconLock) { if (pkg in iconMissing) return }
    val bitmap = loadAppIcon(context, pkg)
    synchronized(iconLock) {
        if (bitmap != null) iconCache.put(pkg, bitmap) else iconMissing.add(pkg)
    }
}

/** 预热代号。切换筛选条件时自增，旧的预热线程看到变了就自己退出，不来抢新列表的解码 */
@Volatile
private var preloadGeneration = 0

/**
 * 预热：按列表顺序在后台把图标逐个解进缓存。
 *
 * 这样滑动时命中缓存、进组合时就直接有图，不再「先显示占位字母、解码完再补一张」——
 * 那个补图动作是一次重组，一屏十几个项同时补就会顿一下。
 *
 * 单线程 + 后台优先级，而且按需队列里有活时主动让路，保证永远先服务屏幕上看到的。
 * 换筛选条件时旧线程会因为代号变了自动退出。
 */
internal fun preloadIcons(context: Context, pkgs: List<String>) {
    if (pkgs.isEmpty()) return
    val generation = ++preloadGeneration
    val job = Runnable {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        var done = 0
        for (pkg in pkgs) {
            if (generation != preloadGeneration) return@Runnable
            if (iconCache.get(pkg) != null) continue
            // 屏幕上正有项等着出图，先让路
            while (iconExecutor.queue.isNotEmpty()) {
                if (generation != preloadGeneration) return@Runnable
                runCatching { Thread.sleep(40) }
            }
            decodeIntoCache(context, pkg)
            if (++done >= ICON_PRELOAD_LIMIT) return@Runnable
        }
    }
    iconThread(job, "app-icon-preload").start()
}

/**
 * 应用图标。
 *
 * 只在**当前可见**的列表项里加载：LazyColumn 是懒布局，滚出屏幕后 [DisposableEffect]
 * 被 dispose，标记失效后即使 worker 线程跑完也不会再触发重组；滚回来时先命中缓存直接显示。
 *
 * 配合 [preloadIcons]：预热过的列表在滑动时几乎全部命中缓存，
 * 既不排队解码、也不触发补图重组，滑动才是顺的。
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
            iconExecutor.execute {
                if (iconCache.get(pkg) == null) decodeIntoCache(context, pkg)
                val image = iconCache.get(pkg)?.asImageBitmap()
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
