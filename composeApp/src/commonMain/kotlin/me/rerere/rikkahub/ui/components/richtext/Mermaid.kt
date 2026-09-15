package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_BASE_URL
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.escapeHtml
import me.rerere.rikkahub.utils.toCssHex
import me.rerere.rikkahub.platform.encodeImageToPng
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@OptIn(ExperimentalResourceApi::class)
@Composable
fun Mermaid(code: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val success = stringResource(Res.string.mermaid_export_success)
    val failure = stringResource(Res.string.mermaid_export_failed)
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val saveLauncher = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { target ->
        val bytes = imageBytes
        if (target != null && bytes != null) {
            scope.launch {
                runCatching { target.write(bytes) }
                    .onSuccess { toaster.show(success, type = ToastType.Success) }
                    .onFailure { toaster.show(failure, type = ToastType.Error) }
            }
        }
        imageBytes = null
    }
    val navigator = rememberWebViewNavigator()
    val bridge = rememberWebViewJsBridge(navigator)
    DisposableEffect(bridge, saveLauncher) {
        val handler = object : IJsMessageHandler {
            override fun methodName(): String = "exportImage"
            override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
                scope.launch {
                    runCatching {
                        val decoded = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
                            .decode(message.params.filterNot(Char::isWhitespace))
                        imageBytes = encodeImageToPng(decoded)!!
                        saveLauncher.launch(
                            suggestedName = "mermaid_${Clock.System.now().toEpochMilliseconds()}",
                            defaultExtension = "png",
                        )
                    }.onFailure {
                        it.printStackTrace()
                        toaster.show(failure, type = ToastType.Error)
                    }
                }
            }
        }
        bridge.register(handler)
        onDispose { bridge.unregister(handler) }
    }
    val script by produceState<String?>(null) {
        value = Res.readBytes("files/html/mermaid.min.js").decodeToString()
    }
    val html = remember(code, colorScheme, script) {
        script?.let { buildMermaidHtml(code, colorScheme, it) }
    } ?: return
    val state = rememberWebViewStateWithHTMLData(html, baseUrl = WEB_VIEW_BASE_URL, mimeType = "text/html")
    Column(modifier) {
        WebView(
            state = state,
            navigator = navigator,
            webViewJsBridge = bridge,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).height(200.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.End).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = {
                val contentId = WebViewContentCache.store(FileKit.cacheDir.toKotlinxIoPath(), html)
                navController.navigate(Screen.WebView(contentId = contentId))
            }) {
                Icon(HugeIcons.View, contentDescription = "Preview")
            }
            IconButton(onClick = { navigator.evaluateJavaScript("exportSvgToPng();") }) {
                Icon(HugeIcons.Download01, contentDescription = stringResource(Res.string.mermaid_export))
            }
        }
    }
}

internal fun buildMermaidHtml(
    code: String,
    colorScheme: ColorScheme,
    mermaidScript: String,
): String {
    val primaryColor = colorScheme.primaryContainer.toCssHex()
    val secondaryColor = colorScheme.secondaryContainer.toCssHex()
    val tertiaryColor = colorScheme.tertiaryContainer.toCssHex()
    val background = colorScheme.background.toCssHex()
    val surface = colorScheme.surface.toCssHex()
    val onPrimary = colorScheme.onPrimaryContainer.toCssHex()
    val onSecondary = colorScheme.onSecondaryContainer.toCssHex()
    val onTertiary = colorScheme.onTertiaryContainer.toCssHex()
    val onBackground = colorScheme.onBackground.toCssHex()
    val errorColor = colorScheme.error.toCssHex()
    val onErrorColor = colorScheme.onError.toCssHex()

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=1024">
            <script>$mermaidScript</script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    display: flex;
                    justify-content: center;
                    background-color: ${background};
                }
                .mermaid {
                    padding: 8px;
                    width: fit-content;
                    min-width: 100%;
                }
                .mermaid svg {
                    background: transparent !important;
                }
            </style>
        </head>
        <body>
            <pre class="mermaid">
                ${code.escapeHtml()}
            </pre>
            <script>
              mermaid.initialize({
                    startOnLoad: true,
                    theme: 'base',
                    themeVariables: {
                        primaryColor: '${primaryColor}',
                        primaryTextColor: '${onPrimary}',
                        primaryBorderColor: '${primaryColor}',

                        secondaryColor: '${secondaryColor}',
                        secondaryTextColor: '${onSecondary}',
                        secondaryBorderColor: '${secondaryColor}',

                        tertiaryColor: '${tertiaryColor}',
                        tertiaryTextColor: '${onTertiary}',
                        tertiaryBorderColor: '${tertiaryColor}',

                        background: '${background}',
                        mainBkg: '${primaryColor}',
                        secondBkg: '${secondaryColor}',

                        lineColor: '${onBackground}',
                        textColor: '${onBackground}',

                        nodeBkg: '${surface}',
                        nodeBorder: '${primaryColor}',
                        clusterBkg: '${surface}',
                        clusterBorder: '${primaryColor}',

                        actorBorder: '${primaryColor}',
                        actorBkg: '${surface}',
                        actorTextColor: '${onBackground}',
                        actorLineColor: '${primaryColor}',

                        taskBorderColor: '${primaryColor}',
                        taskBkgColor: '${primaryColor}',
                        taskTextLightColor: '${onPrimary}',
                        taskTextDarkColor: '${onBackground}',

                        labelColor: '${onBackground}',
                        errorBkgColor: '${errorColor}',
                        errorTextColor: '${onErrorColor}'
                    }
              });

              window.exportSvgToPng = function() {
                try {
                    const svgElement = document.querySelector('.mermaid svg');
                    if (!svgElement) {
                        window.kmpJsBridge.callNative('exportImage', '');
                        return;
                    }

                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');

                    const svgRect = svgElement.getBoundingClientRect();
                    const width = svgRect.width;
                    const height = svgRect.height;

                    const scaleFactor = window.devicePixelRatio * 2;
                    canvas.width = width * scaleFactor;
                    canvas.height = height * scaleFactor;

                    const svgXml = new XMLSerializer().serializeToString(svgElement);
                    const svgBase64 = btoa(unescape(encodeURIComponent(svgXml)));

                    const img = new Image();
                    img.onload = function() {
                        ctx.fillStyle = '${background}';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);
                        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

                        ctx.font = '14px Arial';
                        ctx.fillStyle = '${onBackground}';
                        ctx.fillText('rikka-ai.com', 20, canvas.height - 10);

                        const pngBase64 = canvas.toDataURL('image/png').split(',')[1];
                        window.kmpJsBridge.callNative('exportImage', pngBase64);
                    };
                    img.onerror = function(e) {
                        window.kmpJsBridge.callNative('exportImage', '');
                    }
                    img.src = 'data:image/svg+xml;base64,' + svgBase64;
                } catch (e) {
                    window.kmpJsBridge.callNative('exportImage', '');
                }
              };
            </script>
        </body>
        </html>
    """.trimIndent()
}
