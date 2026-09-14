package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.Svg
import coil3.svg.SvgDecoder
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.utils.computeAIIconByName
import me.rerere.rikkahub.utils.toCssHex
import okio.Buffer

@Composable
private fun AIIcon(
    path: String,
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val contentColor = LocalContentColor.current
    val context = LocalPlatformContext.current
    val model = remember(path, contentColor, context) {
        ImageRequest.Builder(context)
            .data(Res.getUri("files/icons/$path"))
            .memoryCacheKeyExtra("ai-icon-fill", contentColor.toCssHex())
            .decoderFactory(aiIconSvgDecoder(contentColor))
            .build()
    }
    Surface(
        modifier = modifier.size(24.dp),
        shape = rememberAvatarShape(loading),
        color = color,
    ) {
        AsyncImage(
            model = model,
            contentDescription = name,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun aiIconSvgDecoder(contentColor: Color): SvgDecoder.Factory = SvgDecoder.Factory(
    parser = Svg.Parser { source ->
        // Coil's css() is Android-only. Apply the original "svg { fill: ... }" rule to the SVG roots.
        val document = Ksoup.parse(source.readUtf8(), parser = Parser.xmlParser())
        val rgb = listOf(contentColor.red, contentColor.green, contentColor.blue)
            .joinToString(",") { (it * 255).toInt().toString() }
        // Skia's SVG decoder accepts rgba(), but not #RRGGBBAA.
        document.select("svg").attr("fill", "rgba($rgb,${contentColor.alpha})")
        Svg.Parser.DEFAULT.parse(Buffer().writeUtf8(document.outerHtml()))
    },
)

@Composable
fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val path = remember(name) { computeAIIconByName(name) } ?: run {
        TextAvatar(text = name, modifier = modifier, loading = loading, color = color)
        return
    }
    AIIcon(
        path = path,
        name = name,
        modifier = modifier,
        loading = loading,
        color = color,
    )
}
