package me.rerere.rikkahub.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import me.rerere.rikkahub.ui.components.webview.JvmWebViewContentStore

object JvmPlatformRouteContent : PlatformRouteContent {
    @Composable
    override fun Render(screen: Screen) {
        when (screen) {
            is Screen.WebView -> JvmWebViewPage(screen)
            else -> SharedUnavailableRoute(screen)
        }
    }
}

@Composable
private fun JvmWebViewPage(screen: Screen.WebView) {
    val html = JvmWebViewContentStore.load(screen.contentId)
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
                html != null -> HtmlWebView(html)
                screen.url.isNotBlank() -> UrlWebView(screen.url)
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
private fun HtmlWebView(html: String) {
    WebViewWithProgress(
        state = rememberWebViewStateWithHTMLData(
            data = html,
            baseUrl = "https://rikkahub.local",
            mimeType = "text/html",
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun UrlWebView(url: String) {
    WebViewWithProgress(
        state = rememberWebViewState(url),
        modifier = Modifier.fillMaxSize(),
    )
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

@Composable
private fun SharedUnavailableRoute(screen: Screen) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton()
        Text(
            text = screen::class.simpleName ?: "Unavailable route",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "This feature is not available on Desktop yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
