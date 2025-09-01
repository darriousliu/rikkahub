package me.rerere.rikkahub.ui.pages.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.configureZoom
import me.rerere.rikkahub.utils.base64Decode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPage(url: String, content: String) {
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(url = url)
    } else {
        rememberWebViewStateWithHTMLData(data = content.base64Decode())
    }
    val webNavigator = rememberWebViewNavigator()

    var showDropdown by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    BackHandler(webNavigator.canGoBack) {
        webNavigator.navigateBack()
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
                    IconButton(onClick = { webNavigator.reload() }) {
                        Icon(Lucide.RefreshCw, contentDescription = "Refresh")
                    }

                    IconButton(
                        onClick = { webNavigator.navigateForward() },
                        enabled = webNavigator.canGoForward
                    ) {
                        Icon(Lucide.ArrowRight, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(Lucide.EllipsisVertical, contentDescription = "More options")

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in Browser") },
                                leadingIcon = { Icon(Lucide.Earth, contentDescription = null) },
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
                                leadingIcon = { Icon(Lucide.Bug, contentDescription = null) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            onCreated = {
                it.configureZoom()
            }
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

//                SelectionContainer {
//                    LazyColumn {
//                        items(state.consoleMessages) { message ->
//                            Text(
//                                text = "${message.messageLevel().name}: ${message.message()}\n" +
//                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
//                                style = MaterialTheme.typography.bodySmall,
//                                fontFamily = JetbrainsMono,
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .padding(vertical = 4.dp),
//                                color = when (message.messageLevel().name) {
//                                    "ERROR" -> MaterialTheme.colorScheme.error
//                                    "WARNING" -> MaterialTheme.colorScheme.secondary
//                                    else -> MaterialTheme.colorScheme.onSurface
//                                }
//                            )
//                        }
//                    }
//                }
//
//                if (state.consoleMessages.isEmpty()) {
//                    Text(
//                        text = "No console messages",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.padding(16.dp)
//                    )
//                }
            }
        }
    }
}
