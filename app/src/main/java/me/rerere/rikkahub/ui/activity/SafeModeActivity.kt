package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler

class SafeModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = CrashHandler.getStackTrace(this)
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            RikkahubTheme {
                SafeModePage(stackTrace = stackTrace, onEnterApp = {
                    startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                    finish()
                })
            }
        }
    }
}
