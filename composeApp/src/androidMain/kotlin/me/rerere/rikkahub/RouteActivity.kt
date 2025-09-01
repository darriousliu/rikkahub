package me.rerere.rikkahub

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

private const val TAG = "RouteActivity"

class RouteActivity : ComponentActivity() {
    private var navStack by mutableStateOf<NavHostController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        setContent {
            val navStack = rememberNavController()
            this.navStack = navStack
            ShareHandler(navStack)
            App(
                navBackStack = navStack,
                platformConfigure = { darkTheme ->
                    // 更新状态栏图标颜色
                    val view = LocalView.current
                    if (!view.isInEditMode) {
                        SideEffect {
                            val window = (view.context as Activity).window
                            WindowCompat.getInsetsController(window, view).apply {
                                isAppearanceLightStatusBars = !darkTheme
                                isAppearanceLightNavigationBars = !darkTheme
                            }
                        }
                    }
                }
            )
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    @Composable
    private fun ShareHandler(navBackStack: NavHostController) {
        val shareIntent = remember {
            Intent().apply {
                action = intent?.action
                putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
                putExtra(Intent.EXTRA_STREAM, intent?.getStringExtra(Intent.EXTRA_STREAM))
            }
        }

        LaunchedEffect(navBackStack) {
            if (shareIntent.action == Intent.ACTION_SEND) {
                val text = shareIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val imageUri = shareIntent.getStringExtra(Intent.EXTRA_STREAM)
                navBackStack.navigate(Screen.ShareHandler(text, imageUri))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Navigate to the chat screen if a conversation ID is provided
        intent.getStringExtra("conversationId")?.let { text ->
            navStack?.navigate(Screen.Chat(text))
        }
    }
}
