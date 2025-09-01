package me.rerere.rikkahub.ui.components.webview

import com.multiplatform.webview.web.AccompanistWebViewClient
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams

actual val platformWebviewParams: PlatformWebViewParams = PlatformWebViewParams(
    client = AccompanistWebViewClient(),
    chromeClient = MyWebChromeClient(),
)

actual fun NativeWebView.configureZoom() {
    settings.apply {
        builtInZoomControls = true
        displayZoomControls = false
    }
}

actual fun NativeWebView.configureJsBridge(block: NativeWebView.() -> Unit) {
    block()
}
