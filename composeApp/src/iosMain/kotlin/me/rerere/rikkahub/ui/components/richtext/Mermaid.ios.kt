package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewState

@Composable
actual fun rememberWebViewJsBridgeOrNull(): WebViewJsBridge? {
    return rememberWebViewJsBridge()
}

actual fun NativeWebView.mermaidInterface(
    onHeightChanged: (Int) -> Unit,
    onExportImage: (String) -> Unit
) {
}

actual fun callJsBridge(
    method: String,
    params: String,
    callback: ((String) -> Unit)?
): String {
    return """window.kmpJsBridge.callNative('MermaidInterface', $params);"""
}

actual fun evaluateJs(
    state: WebViewState,
    code: String,
    callback: ((String) -> Unit)?
) {
    state.nativeWebView.evaluateJavaScript(code) { result, error ->
        if (callback == null) return@evaluateJavaScript
        if (error != null) {
            callback.invoke(error.localizedDescription())
        } else {
            callback.invoke(result?.toString() ?: "")
        }
    }
}
