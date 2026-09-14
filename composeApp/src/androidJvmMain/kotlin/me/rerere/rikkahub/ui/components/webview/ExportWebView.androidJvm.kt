package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.web.WebViewState

@Composable
internal actual fun ExportWebView(state: WebViewState, modifier: Modifier) {
    // Desktop export intentionally leaves native WebView regions empty.
    Box(modifier)
}
