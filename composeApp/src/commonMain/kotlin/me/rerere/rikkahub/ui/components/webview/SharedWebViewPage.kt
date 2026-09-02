package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewState
import io.github.kdroidfilter.webview.web.rememberWebViewState
import io.github.kdroidfilter.webview.web.rememberWebViewStateWithHTMLData
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton

private const val LOCAL_CONTENT_BASE_URL = "https://rikkahub.local"

/**
 * WebView 页面，iOS 与 Desktop 共用。
 *
 * 用 Column 而不是 Scaffold 的 contentPadding 布局：WebView 在 iOS 上是原生 interop 视图，
 * 让它的 frame 严格落在标题栏之下，避免与标题栏发生任何重叠或事件争抢。
 *
 * Android 仍走 `:app` 里基于 WebViewAssetLoader 的实现，因为它还要服务 Mermaid 的本地资源。
 */
@Composable
fun SharedWebViewPage(screen: Screen.WebView) {
    val html = WebViewContentStore.load(screen.contentId)
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = if (html != null) "Preview" else screen.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = { BackButton(modifier = Modifier.padding(horizontal = 8.dp)) },
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                html != null -> WebViewWithProgress(
                    state = rememberWebViewStateWithHTMLData(
                        data = html,
                        baseUrl = LOCAL_CONTENT_BASE_URL,
                        mimeType = "text/html",
                    ),
                )

                screen.url.isNotBlank() -> WebViewWithProgress(state = rememberWebViewState(screen.url))

                else -> Text(
                    text = "Web content is no longer available.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WebViewWithProgress(state: WebViewState) {
    Box(modifier = Modifier.fillMaxSize()) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
