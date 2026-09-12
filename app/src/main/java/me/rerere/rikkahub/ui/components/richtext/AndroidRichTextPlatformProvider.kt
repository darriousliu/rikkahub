package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.utils.toAndroidUri

@Composable
fun rememberAndroidRichTextPlatformActions(navigator: Navigator): RichTextPlatformActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCode by remember { mutableStateOf("") }
    val createDocumentLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { target ->
        val uri = target?.toAndroidUri() ?: return@rememberFileSaverLauncher
        val code = pendingCode
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(code.encodeToByteArray())
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    return RichTextPlatformActions(
        saveCode = { suggestedName, code ->
            pendingCode = code
            createDocumentLauncher.launch(suggestedName = suggestedName, defaultExtension = null)
        },
        openCodePreview = { code, language ->
            val contentId = WebViewContentCache.store(
                context.cacheDir,
                buildCodePreviewHtml(code = code, language = language),
            )
            navigator.navigate(Screen.WebView(contentId = contentId))
        },
        codeBlockPreviewRenderer = { code, language, modifier ->
            AndroidCodeBlockPreview(code = code, language = language, modifier = modifier)
        },
        mermaidRenderer = { code, modifier ->
            Mermaid(code = code, modifier = modifier)
        },
    )
}

@Composable
private fun AndroidCodeBlockPreview(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = buildCodePreviewHtml(code = code, language = language),
        baseUrl = "https://rikkahub.local",
        mimeType = "text/html",
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )

    WebView(
        state = state,
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
    )
}

private fun buildCodePreviewHtml(code: String, language: String): String =
    if (language == "svg") {
        """<!DOCTYPE html><html><body style="margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;">$code</body></html>"""
    } else {
        code
    }
