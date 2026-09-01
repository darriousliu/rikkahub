package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Android 仍走 `:app` 里基于 WebViewAssetLoader 的实现，因为它还要服务 Mermaid 的本地资源。
 */
@Composable
fun SharedWebViewPage(screen: Screen.WebView) {
    val html = WebViewContentStore.load(screen.contentId)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton()
                Text(
                    text = if (html != null) "Preview" else screen.url,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                html != null -> WebViewWithProgress(
                    state = rememberWebViewStateWithHTMLData(
                        data = html,
                        baseUrl = LOCAL_CONTENT_BASE_URL,
                        mimeType = "text/html",
                    ),
                    modifier = Modifier.fillMaxSize(),
                )

                screen.url.isNotBlank() -> WebViewWithProgress(
                    state = rememberWebViewState(screen.url),
                    modifier = Modifier.fillMaxSize(),
                )

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
private fun WebViewWithProgress(state: WebViewState, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
