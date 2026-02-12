package me.rerere.rikkahub.ui.components.richtext

import android.webkit.JavascriptInterface

internal actual class GlobalMermaidInterface actual constructor(
    actual val onRenderSuccess: (String, String, Int) -> Unit,
    actual val onRenderError: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onRenderSuccess(id: String, base64: String, height: Int) {
        onRenderSuccess.invoke(id, base64, height)
    }

    @JavascriptInterface
    fun onRenderError(id: String, error: String) {
        onRenderError.invoke(id, error)
    }
}
