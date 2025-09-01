package me.rerere.rikkahub.ui.components.richtext

import android.webkit.JavascriptInterface
import me.rerere.rikkahub.ui.components.webview.NativeWebView

internal actual class MermaidInterface actual constructor(
    actual val onHeightChanged: (Int) -> Unit,
    actual val onExportImage: (String) -> Unit
) {
    @JavascriptInterface
    fun updateHeight(height: Int) {
        onHeightChanged(height)
    }

    @JavascriptInterface
    fun exportImage(base64Image: String) {
        onExportImage(base64Image)
    }
}

actual const val jsBridgePrefix: String = "AndroidInterface"

actual fun NativeWebView.disableScroll() {
    this.isVerticalScrollBarEnabled = false
    this.isHorizontalScrollBarEnabled = false
}
