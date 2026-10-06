package me.frk2222.autoscreenoffcode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 关于页。
 *
 * ★ 项目地址写在下面的 [GITHUB_URL]，改这一处即可，其余内容后续你自己往这几个 item 里加就行。
 */
private const val GITHUB_URL = "https://github.com/frk2222/autoScreenOff"

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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "无操作息屏",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = tonePrimary(),
                    )
                    Hint("按「无操作时长」强制息屏，不看是否在播放视频。")
                    Text(
                        text = "版本 $version",
                        fontSize = 13.sp,
                        color = toneMuted(),
                    )
                }
            }
        }

        item { SmallTitle(text = "项目") }

        item {
            ArrowPreference(
                title = "GitHub",
                summary = GITHUB_URL,
                onClick = { openFailed = !openUrl(context, GITHUB_URL) },
            )
        }

        if (openFailed) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Hint("没有可用的浏览器，请手动打开：$GITHUB_URL")
                }
            }
        }

        item { SmallTitle(text = "紧急自救") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Hint(
                    "万一开机反复重启：" +
                        "① 开机连续按音量键进入 Recovery 安全模式，再到 LSPosed 取消勾选本模块；" +
                        "② 有 root shell 时执行 setprop persist.sys.autoscreenoff.disable 1，模块会自行跳过注册。" +
                        "恢复后请把首页的「① 启用系统框架息屏」关掉，并把 adb logcat -s AutoScreenOff 的报错发出来。",
                )
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    true
}.getOrDefault(false)
