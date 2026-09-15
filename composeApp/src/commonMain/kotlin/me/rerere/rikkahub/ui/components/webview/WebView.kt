package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.utils.JsonInstant
import dev.nucleusframework.webview.web.WebView as KmpWebView

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
    // Replace native views with their platform export representation.
    if (LocalExportContext.current && currentPlatformKind != PlatformKind.ANDROID) {
        ExportWebView(state, modifier)
        return
    }
    val currentOnConsoleMessage by rememberUpdatedState(onConsoleMessage)
    configureWebViewState(state)
    // Attach platform hooks before the library's load/navigation effects start. In 1.0.3,
    // LocalWebViewFactory is a test hook that suppresses the visible native view on every platform.
    val webView = state.webView
    DisposableEffect(webView, state) {
        webView?.let { configureNativeWebView(it.nativeWebView, state) { currentOnConsoleMessage(it) } }
        onDispose { }
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
        WebViewLifecycle { onDispose ->
            KmpWebView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                navigator = navigator,
                webViewJsBridge = webViewJsBridge,
                onDispose = onDispose,
            ) {
                // Tao's NativeView needs overlays inside this slot to draw above the native web surface.
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { (state.loadingState as? LoadingState.Loading)?.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// The library owns loading/navigation state. Only missing native settings and console callbacks live here.
internal expect fun configureWebViewState(state: WebViewState)

internal expect fun configureNativeWebView(
    view: NativeWebView,
    state: WebViewState,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
)

internal expect fun disposeWebView(view: NativeWebView)

@Composable
internal expect fun WebViewLifecycle(content: @Composable ((NativeWebView) -> Unit) -> Unit)

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

@Composable
internal expect fun ExportWebView(state: WebViewState, modifier: Modifier)
