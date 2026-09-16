package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import com.hashsequence.coilresvg.ResvgDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.rerere.mermaid.renderMermaidSvg
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

internal data class DiagramSource(val code: String, val language: String, val config: String = "{}") {
    val cacheKey: String = "native-diagram-v1:$language:${config.encodeUtf8().sha256().hex()}:" +
        code.encodeUtf8().sha256().hex()
}

@Composable
internal fun rememberDiagramSource(code: String, language: String): DiagramSource {
    val colorScheme = MaterialTheme.colorScheme
    return remember(code, language, colorScheme) {
        DiagramSource(code, language, if (language == "mermaid") mermaidThemeConfig(colorScheme) else "{}")
    }
}

internal fun diagramImageRequest(context: PlatformContext, diagram: DiagramSource): ImageRequest.Builder =
    ImageRequest.Builder(context)
        .data(diagram)
        .memoryCacheKey(diagram.cacheKey)
        .fetcherFactory(DiagramFetcherFactory, DiagramSource::class)
        .decoderFactory(ResvgDecoder.Factory())

private object DiagramFetcherFactory : Fetcher.Factory<DiagramSource> {
    override fun create(data: DiagramSource, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
        // Coil owns cancellation and bitmap caching. Both native stages run away from the UI thread.
        val svg = withContext(Dispatchers.Default) {
            val result = if (data.language == "mermaid") {
                checkNotNull(renderMermaidSvg(data.code, data.config)) { "No Mermaid diagram to render" }
            } else {
                data.code
            }
            ensureActive()
            result
        }
        SourceFetchResult(
            source = ImageSource(Buffer().writeUtf8(svg), options.fileSystem),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY,
        )
    }
}
