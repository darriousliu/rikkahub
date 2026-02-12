package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.webview.OffScreenWebViewManager
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.toCssHex
import me.rerere.rikkahub.utils.toImage
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Clock

private val mermaidImageCache = Cache.Builder<String, ByteArray>()
    .maximumCacheSize(50)
    .build()

@Composable
fun MermaidImage(
    code: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val colorScheme = MaterialTheme.colorScheme
    val darkMode = LocalDarkMode.current
    val density = LocalDensity.current
    val toaster = LocalToaster.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = if (maxWidth != Dp.Infinity) {
            with(density) { maxWidth.toPx().toInt() }
        } else {
            0
        }

        val cacheKey = "${code.hashCode()}_${if (darkMode) "dark" else "light"}_$containerWidth"
        var imageByteArray by remember(cacheKey) { mutableStateOf(mermaidImageCache.get(cacheKey)) }

        LaunchedEffect(code, darkMode, colorScheme, containerWidth) {
            if (imageByteArray == null) {
                val result = GlobalMermaidRenderer.render(
                    code = code,
                    theme = if (darkMode) MermaidTheme.DARK else MermaidTheme.DEFAULT,
                    colorScheme = colorScheme,
                    width = containerWidth
                )
                if (result != null) {
                    imageByteArray = Base64.decode(result.base64)
                    mermaidImageCache.put(cacheKey, imageByteArray!!)
                }
            }
        }

        if (imageByteArray != null) {
            Box {
                AsyncImage(
                    model = remember(imageByteArray) {
                        try {
                            ImageRequest.Builder(context)
                                .data(imageByteArray)
                                .build()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    },
                    contentDescription = "Mermaid Diagram",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth
                )
                // 导出图片按钮
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
//                            preview = true
                        },
                    ) {
                        Icon(
                            Lucide.Eye,
                            contentDescription = "Prewview"
                        )
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                // 解码Base64图像并保存
                                try {
                                    val imageBytes = Base64.decode(imageByteArray!!)
                                    val bitmap = imageBytes.toImage()
                                    context.exportImage(
                                        context,
                                        bitmap,
                                        "mermaid_${Clock.System.now().toEpochMilliseconds()}.png"
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                toaster.show(
                                    runBlocking { getString(Res.string.mermaid_export_success) },
                                    type = ToastType.Success
                                )
                            }.onFailure {
                                it.printStackTrace()
                                toaster.show(
                                    runBlocking { getString(Res.string.mermaid_export_failed) },
                                    type = ToastType.Error
                                )
                            }
                        },
                    ) {
                        Icon(
                            Lucide.Download,
                            contentDescription = stringResource(Res.string.mermaid_export)
                        )
                    }
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun LoadMermaidEffect() {
    val isDarkMode = LocalDarkMode.current
    val theme = MaterialTheme.colorScheme
    LaunchedEffect(isDarkMode, theme) {
        OffScreenWebViewManager.webView?.loadData(
            html = buildGlobalMermaidHtml(
                colorScheme = theme
            ),
            baseUrl = null,
            mimeType = "text/html",
            encoding = "UTF-8",
            historyUrl = null
        )
    }
}

private data class RenderResult(val base64: String, val height: Int)

suspend fun initMermaidRenderer() {
    GlobalMermaidRenderer.init()
}

private object GlobalMermaidRenderer {
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<RenderResult>>()
    private val mutex = Mutex()
    private var initialized = false

    suspend fun init() {
        mutex.withLock {
            if (initialized) return
            withContext(Dispatchers.Main) {
                OffScreenWebViewManager.init()

                val jsInterface = GlobalMermaidInterface(
                    onRenderSuccess = { id, base64, height ->
                        pendingRequests[id]?.complete(RenderResult(base64, height))
                        pendingRequests.remove(id)
                    },
                    onRenderError = { id, error ->
                        pendingRequests[id]?.completeExceptionally(Exception(error))
                        pendingRequests.remove(id)
                    }
                )

                OffScreenWebViewManager.addJsBridge("AndroidInterface", jsInterface)

//                val html = buildGlobalMermaidHtml()
//                OffScreenWebViewManager.webView?.loadData(
//                    html = html,
//                    baseUrl = null,
//                    mimeType = "text/html",
//                    encoding = "UTF-8",
//                    historyUrl = null
//                )
                initialized = true
            }
        }
    }

    suspend fun render(code: String, theme: MermaidTheme, colorScheme: ColorScheme, width: Int): RenderResult? {
        if (!initialized) init()

        val id = "req_${Random.nextInt()}"
        val deferred = CompletableDeferred<RenderResult>()

        mutex.withLock {
            pendingRequests[id] = deferred
        }

        val themeVariables = buildThemeVariablesJson(colorScheme)
        val escapedCode = code.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val script = "renderMermaid('$id', '$escapedCode', '${theme.value}', $themeVariables, $width);"

        withContext(Dispatchers.Main) {
            OffScreenWebViewManager.evaluateJavascript(script)
        }

        return try {
            deferred.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

internal expect class GlobalMermaidInterface(
    onRenderSuccess: (String, String, Int) -> Unit,
    onRenderError: (String, String) -> Unit
) {
    internal val onRenderSuccess: (String, String, Int) -> Unit
    internal val onRenderError: (String, String) -> Unit
}

private fun buildThemeVariablesJson(colorScheme: ColorScheme): String {
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
        {
            "primaryColor": "$primaryColor",
            "primaryTextColor": "$onPrimary",
            "primaryBorderColor": "$primaryColor",
            "secondaryColor": "$secondaryColor",
            "secondaryTextColor": "$onSecondary",
            "secondaryBorderColor": "$secondaryColor",
            "tertiaryColor": "$tertiaryColor",
            "tertiaryTextColor": "$onTertiary",
            "tertiaryBorderColor": "$tertiaryColor",
            "background": "$background",
            "mainBkg": "$primaryColor",
            "secondBkg": "$secondaryColor",
            "lineColor": "$onBackground",
            "textColor": "$onBackground",
            "nodeBkg": "$surface",
            "nodeBorder": "$primaryColor",
            "clusterBkg": "$surface",
            "clusterBorder": "$primaryColor",
            "actorBorder": "$primaryColor",
            "actorBkg": "$surface",
            "actorTextColor": "$onBackground",
            "actorLineColor": "$primaryColor",
            "taskBorderColor": "$primaryColor",
            "taskBkgColor": "$primaryColor",
            "taskTextLightColor": "$onPrimary",
            "taskTextDarkColor": "$onBackground",
            "labelColor": "$onBackground",
            "errorBkgColor": "$errorColor",
            "errorTextColor": "$onErrorColor"
        }
    """.trimIndent()
}

private fun buildGlobalMermaidHtml(
    colorScheme: ColorScheme,
): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes, maximum-scale=5.0">
            <title>Mermaid Diagram</title>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: transparent;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: auto;
                    background-color: ${colorScheme.background.toCssHex()};
                }
                .mermaid {
                    width: 100%;
                    padding: 8px;
                }
            </style>
        </head>
        <body>
            <script>
              window.renderMermaid = async function(id, code, theme, themeVariables, width) {
                  // Use separate IDs for container and SVG to avoid conflicts
                  const containerId = 'container-' + id;
                  const svgId = 'svg-' + id;

                  mermaid.initialize({
                    startOnLoad: false,
                    theme: theme,
                    themeVariables: themeVariables
                  });

                  let container = null;
                  try {
                      container = document.createElement('div');
                      container.id = containerId;
                      container.className = 'mermaid';
                      if (width > 0) {
                          container.style.width = width + 'px';
                      }
                      console.log("Rendering mermaid diagram with id:", id, "width:", width);

                      // Ensure container is appended to body
                      if (document.body) {
                          document.body.appendChild(container);
                      } else {
                          throw new Error("document.body is null");
                      }


                      // Pass svgId for the SVG element, not containerId
                      const { svg } = await mermaid.render(svgId, code);
                      console.log("svg generated length:", svg.length);
                      if (!container) {
                          throw new Error("Container disappeared");
                      }
                      container.innerHTML = svg;

                      const svgElement = container.querySelector('svg');
                      if (!svgElement) throw new Error("SVG element not found in container");

                      const svgRect = svgElement.getBoundingClientRect();
                      const svgWidth = svgRect.width;
                      const svgHeight = svgRect.height;
                      console.log("SVG dimensions:", svgWidth, svgHeight);

                      if (svgWidth === 0 || svgHeight === 0) {
                           // Try to get width/height from attributes if BBox failed (e.g. detached)
                            console.warn("BoundingClientRect is zero, checking attributes");
                       }

                      const canvas = document.createElement('canvas');
                      const ctx = canvas.getContext('2d');
                      const scaleFactor = window.devicePixelRatio * 2; // Increase resolution
                      canvas.width = svgWidth * scaleFactor;
                      canvas.height = svgHeight * scaleFactor;
                      // 保证分辨率
                      if (canvas.width < width) {
                          canvas.width = width;
                          canvas.height = width * (svgHeight / svgWidth);
                      }
                      console.log("Canvas created with size:", canvas.width, canvas.height);

                      const svgXml = new XMLSerializer().serializeToString(svgElement);
                      const svgBase64 = btoa(unescape(encodeURIComponent(svgXml)));

                      const img = new Image();
                      img.onload = function() {
                          if (themeVariables.background) {
                              ctx.fillStyle = themeVariables.background;
                              ctx.fillRect(0, 0, canvas.width, canvas.height);
                          }

                          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

//                          ctx.font = '14px Arial';
//                          ctx.fillStyle = themeVariables.textColor || 'black';
//                          ctx.fillText('rikka-ai.com', 20, canvas.height - 10);

                          const pngBase64 = canvas.toDataURL('image/png').split(',')[1];

//                          if (container && container.parentNode) {
//                              container.parentNode.removeChild(container);
//                          }

                          // Check if Android interface has methods or is a function (iOS)
                          if (window.AndroidInterface && typeof window.AndroidInterface.onRenderSuccess === 'function') {
                              window.AndroidInterface.onRenderSuccess(id, pngBase64, Math.ceil(svgHeight));
                          } else {
                              // iOS or fallback
                              var msg = {
                                  method: 'onRenderSuccess',
                                  id: id,
                                  base64: pngBase64,
                                  height: Math.ceil(svgHeight)
                              };
                              window.AndroidInterface(JSON.stringify(msg));
                          }
                      };
                      img.onerror = function(e) {
                          if (container && container.parentNode) {
                              container.parentNode.removeChild(container);
                          }
                          if (window.AndroidInterface && typeof window.AndroidInterface.onRenderError === 'function') {
                              window.AndroidInterface.onRenderError(id, "Image load error");
                          } else {
                              var msg = {
                                  method: 'onRenderError',
                                  id: id,
                                  error: "Image load error"
                              };
                              window.AndroidInterface(JSON.stringify(msg));
                          }
                      }
                      img.src = 'data:image/svg+xml;base64,' + svgBase64;

                  } catch (e) {
                      if (container && container.parentNode) {
                          container.parentNode.removeChild(container);
                      }
                      if (window.AndroidInterface && typeof window.AndroidInterface.onRenderError === 'function') {
                          window.AndroidInterface.onRenderError(id, e.toString());
                      } else {
                          var msg = {
                              method: 'onRenderError',
                              id: id,
                              error: e.toString()
                          };
                          window.AndroidInterface(JSON.stringify(msg));
                      }
                  }
              };
            </script>
        </body>
        </html>
    """.trimIndent()
}
