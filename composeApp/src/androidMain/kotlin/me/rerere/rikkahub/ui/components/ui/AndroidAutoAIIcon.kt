package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.css
import me.rerere.rikkahub.utils.computeAIIconByName
import me.rerere.rikkahub.utils.toCssHex

@Composable
actual fun AutoAIIcon(
    name: String,
    modifier: Modifier,
    loading: Boolean,
    color: Color,
) {
    val path = remember(name) { computeAIIconByName(name) }
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = color,
    ) {
        if (path == null) {
            ProviderLetterAvatar(name)
        } else {
            val contentColor = LocalContentColor.current
            val context = LocalContext.current
            val model = remember(path, contentColor, context) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/icons/$path")
                    .css("svg { fill: ${contentColor.toCssHex()}; }")
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = name,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun ProviderLetterAvatar(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
