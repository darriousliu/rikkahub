package me.rerere.rikkahub.ui.components.richtext

import me.rerere.rikkahub.ui.components.webview.NativeWebView

internal actual class MermaidInterface actual constructor(
    actual val onHeightChanged: (Int) -> Unit,
    actual val onExportImage: (String) -> Unit
)

actual const val jsBridgePrefix: String = "window"

actual fun NativeWebView.disableScroll() {
    scrollView.scrollEnabled = false
    scrollView.bounces = false
    scrollView.alwaysBounceVertical = false
    scrollView.alwaysBounceHorizontal = false
    scrollView.showsVerticalScrollIndicator = false
    scrollView.showsHorizontalScrollIndicator = false
}
