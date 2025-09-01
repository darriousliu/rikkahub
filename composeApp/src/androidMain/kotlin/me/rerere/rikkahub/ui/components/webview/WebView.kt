package me.rerere.rikkahub.ui.components.webview

import android.webkit.ConsoleMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.multiplatform.webview.web.AccompanistWebChromeClient

private const val TAG = "WebView"

internal class MyWebChromeClient() : AccompanistWebChromeClient() {
    // --- Console Message ---
    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
        internal set

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        pushConsoleMessage(consoleMessage)
        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR || consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
            Logger.e(TAG) { "onConsoleMessage:  ${consoleMessage.message()}  ${consoleMessage.lineNumber()}  ${consoleMessage.sourceId()}" }
        }
        return super.onConsoleMessage(consoleMessage);
    }

    fun pushConsoleMessage(message: ConsoleMessage) {
        consoleMessages = consoleMessages + message
        if (consoleMessages.size > 64) { // Limit to 64 messages
            consoleMessages = consoleMessages.takeLast(64)
        }
    }
}
