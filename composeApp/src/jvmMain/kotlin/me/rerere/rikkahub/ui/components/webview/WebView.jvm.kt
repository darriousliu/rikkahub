package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewState

internal actual fun configureWebViewState(state: WebViewState) {
    state.webSettings.desktopWebSettings.initScript = webViewConsoleScript(
        "window.ipc.postMessage(JSON.stringify({methodName:'console',params:JSON.stringify(entry),callbackId:-1}));",
    )
}

internal actual fun configureNativeWebView(
    view: NativeWebView,
    state: WebViewState,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
) = Unit

internal actual fun disposeWebView(view: NativeWebView) = Unit
