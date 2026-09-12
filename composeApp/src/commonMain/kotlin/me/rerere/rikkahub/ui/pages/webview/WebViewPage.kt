package me.rerere.rikkahub.ui.pages.webview

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.platform.PlatformBackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_BASE_URL
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import io.github.kdroidfilter.webview.web.rememberWebViewState
import io.github.kdroidfilter.webview.web.rememberWebViewStateWithHTMLData
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.WebContent
import androidx.compose.runtime.mutableStateListOf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import me.rerere.rikkahub.ui.components.webview.WebViewConsoleMessage
import me.rerere.rikkahub.ui.theme.jetbrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPage(url: String, contentId: String) {
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(url = url)
    } else {
        val content = remember(contentId) {
            WebViewContentCache.load(FileKit.cacheDir.toKotlinxIoPath(), contentId).orEmpty()
        }
        rememberWebViewStateWithHTMLData(data = content, baseUrl = WEB_VIEW_BASE_URL, mimeType = "text/html")
    }
    val navigator = rememberWebViewNavigator()
    val consoleMessages = remember { mutableStateListOf<WebViewConsoleMessage>() }

    var showDropdown by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    PlatformBackHandler(navigator.canGoBack) {
        navigator.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.lastLoadedUrl
                        ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = {
                        val content = state.content
                        if (content is WebContent.Data) {
                            navigator.loadHtml(
                                content.data, content.baseUrl, content.mimeType, content.encoding, content.historyUrl,
                            )
                        } else {
                            navigator.reload()
                        }
                    }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "Refresh")
                    }

                    IconButton(
                        onClick = { navigator.navigateForward() },
                        enabled = navigator.canGoForward
                    ) {
                        Icon(HugeIcons.ArrowRight01, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "More options")

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in Browser") },
                                leadingIcon = { Icon(HugeIcons.Earth, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    state.lastLoadedUrl?.let { url ->
                                        if (url.isNotBlank()) {
                                            urlHandler.openUri(url)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Console Logs") },
                                leadingIcon = { Icon(HugeIcons.Bug01, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    showConsoleSheet = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) {
        WebView(
            state = state,
            navigator = navigator,
            onConsoleMessage = { message ->
                consoleMessages.add(message)
                if (consoleMessages.size > 64) consoleMessages.removeAt(0)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        )
    }

    if (showConsoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Console Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SelectionContainer {
                    LazyColumn {
                        items(consoleMessages) { message ->
                            Text(
                                text = "${message.level}: ${message.message}\n" +
                                    "Source: ${message.sourceId}:${message.lineNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = jetbrainsMonoFontFamily(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = when (message.level) {
                                    "ERROR" -> MaterialTheme.colorScheme.error
                                    "WARNING" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                if (consoleMessages.isEmpty()) {
                    Text(
                        text = "No console messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
