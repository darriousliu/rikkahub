package me.rerere.rikkahub.ui.components.webview

import io.github.kdroidfilter.webview.web.NativeWebView
import io.github.kdroidfilter.webview.web.WebViewFactoryParam
import io.github.kdroidfilter.webview.web.defaultWebViewFactory

internal actual fun createWebView(
    param: WebViewFactoryParam,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
): NativeWebView {
    param.state.webSettings.desktopWebSettings.initScript = webViewConsoleScript(
        "window.ipc.postMessage(JSON.stringify({methodName:'console',params:JSON.stringify(entry),callbackId:-1}));",
    )
    return defaultWebViewFactory(param)
}

internal actual fun disposeWebView(view: NativeWebView) = Unit
