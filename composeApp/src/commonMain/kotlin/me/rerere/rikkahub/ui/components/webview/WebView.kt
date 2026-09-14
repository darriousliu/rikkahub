package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.jsbridge.IJsMessageHandler
import io.github.kdroidfilter.webview.jsbridge.JsMessage
import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import io.github.kdroidfilter.webview.jsbridge.rememberWebViewJsBridge
import io.github.kdroidfilter.webview.web.LoadingState
import io.github.kdroidfilter.webview.web.NativeWebView
import io.github.kdroidfilter.webview.web.WebViewFactoryParam
import io.github.kdroidfilter.webview.web.WebViewNavigator
import io.github.kdroidfilter.webview.web.WebViewState
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.utils.JsonInstant
import io.github.kdroidfilter.webview.web.WebView as KmpWebView

const val WEB_VIEW_BASE_URL = "https://rikkahub.local"

@Serializable
data class WebViewConsoleMessage(
    val level: String,
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
)

@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge = rememberWebViewJsBridge(navigator),
    onConsoleMessage: (WebViewConsoleMessage) -> Unit = {},
) {
    // Desktop image export captures Compose only; keep the preview's layout without creating a native view.
    if (LocalExportContext.current && currentPlatformKind == PlatformKind.DESKTOP) {
        Box(modifier)
        return
    }
    val currentOnConsoleMessage by rememberUpdatedState(onConsoleMessage)
    val factory = remember {
        { param: WebViewFactoryParam -> createWebView(param) { currentOnConsoleMessage(it) } }
    }
    DisposableEffect(webViewJsBridge) {
        val handler = object : IJsMessageHandler {
            override fun methodName(): String = "console"
            override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
                currentOnConsoleMessage(JsonInstant.decodeFromString<WebViewConsoleMessage>(message.params))
            }
        }
        webViewJsBridge.register(handler)
        onDispose { webViewJsBridge.unregister(handler) }
    }
    state.webSettings.apply {
        isJavaScriptEnabled = true
        androidWebSettings.useWideViewPort = true
    }
    Box(modifier) {
        KmpWebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            navigator = navigator,
            webViewJsBridge = webViewJsBridge,
            factory = factory,
            onDispose = ::disposeWebView,
        )
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { (state.loadingState as? LoadingState.Loading)?.progress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// The library owns loading/navigation state. Only missing native settings and console callbacks live here.
internal expect fun createWebView(
    param: WebViewFactoryParam,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
): NativeWebView

internal expect fun disposeWebView(view: NativeWebView)

internal fun webViewConsoleScript(sendMessage: String): String = """
    (function() {
        function send(level, message, source, line) {
            var entry = {level: level, message: message, sourceId: source || location.href, lineNumber: line || 0};
            $sendMessage
        }
        ['log', 'info', 'debug', 'warn', 'error'].forEach(function(name) {
            var original = console[name];
            console[name] = function() {
                var args = Array.prototype.slice.call(arguments);
                var caller = (new Error().stack || '').split('\n').slice(2).join('\n');
                var position = caller.match(/(?:@|\()(.+):(\d+):\d+\)?/);
                send(name === 'warn' ? 'WARNING' : name === 'info' ? 'TIP' : name.toUpperCase(),
                    args.map(String).join(' '), position && position[1], position ? Number(position[2]) : 0);
                original.apply(console, args);
            };
        });
        window.addEventListener('error', function(event) {
            send('ERROR', event.message, event.filename, event.lineno);
        });
    })();
""".trimIndent()
