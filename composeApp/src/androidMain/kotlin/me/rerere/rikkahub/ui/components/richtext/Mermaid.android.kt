package me.rerere.rikkahub.ui.components.richtext

import android.webkit.JavascriptInterface
import androidx.compose.runtime.Composable
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject

@Composable
actual fun rememberWebViewJsBridgeOrNull(): WebViewJsBridge? {
    return null
}

/**
 * JavaScript interface to receive height updates and handle image export from the WebView
 */
private class AndroidMermaidInterface(
    private val onHeightChanged: (Int) -> Unit,
    private val onExportImage: (String) -> Unit
) {
    @JavascriptInterface
    fun updateHeight(height: String) {
        Json.parseToJsonElement(height).jsonObject["height"]?.let {
            if (it is JsonPrimitive && it.isString) {
                onHeightChanged(it.content.toInt())
            } else if (it is JsonPrimitive) {
                onHeightChanged(it.int)
            }
        }
    }

    @JavascriptInterface
    fun exportImage(base64Image: String) {
        Json.parseToJsonElement(base64Image).jsonObject["image"]?.let {
            if (it !is JsonPrimitive || !it.isString) return
            onExportImage(it.content)
        }
    }
}

actual fun NativeWebView.mermaidInterface(
    onHeightChanged: (Int) -> Unit,
    onExportImage: (String) -> Unit
) {
    addJavascriptInterface(
        AndroidMermaidInterface(onHeightChanged, onExportImage),
        "MermaidInterface"
    )
}

actual fun callJsBridge(
    method: String,
    params: String,
    callback: ((String) -> Unit)?
): String {
    return """MermaidInterface.$method($params);"""
}

actual fun evaluateJs(state: WebViewState, code: String, callback: ((String) -> Unit)?) {
    state.nativeWebView.evaluateJavascript(code) {
        callback?.invoke(it)
    }
}
