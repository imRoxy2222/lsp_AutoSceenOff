package me.frk2222.autoscreenoffcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.frk2222.autoscreenoffcode.data.Framework
import me.frk2222.autoscreenoffcode.ui.AppRoot
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Framework.bind()
        setContent {
            MiuixTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Framework.refresh()
    }
}
