package io.github.imroxy2222.autoscreenoffcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.imroxy2222.autoscreenoffcode.data.Framework
import io.github.imroxy2222.autoscreenoffcode.ui.AppRoot
import io.github.imroxy2222.autoscreenoffcode.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Framework.bind()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Framework.refresh()
    }
}
