package me.rerere.rikkahub.ui.components.webview

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams

actual val platformWebviewParams: PlatformWebViewParams = PlatformWebViewParams()

actual fun NativeWebView.configureZoom() {
}

actual fun NativeWebView.configureJsBridge(block: NativeWebView.() -> Unit) {
}
