package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import me.rerere.rikkahub.platform.PlatformQrCodeRenderer

@Composable
fun QRCode(
    value: String,
    modifier: Modifier = Modifier,
    size: Int = 512,
    color: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
) {
    val actualColor = color.takeOrElse { MaterialTheme.colorScheme.secondary }
    val actualBackgroundColor = backgroundColor.takeOrElse { Color.Transparent }
    val renderer = remember { PlatformQrCodeRenderer() }
    val matrix = remember(value, size) {
        renderer.render(content = value, size = size).getOrThrow()
    }

    Canvas(modifier = modifier) {
        if (actualBackgroundColor != Color.Transparent) {
            drawRect(actualBackgroundColor)
        }
        val moduleWidth = this.size.width / matrix.width
        val moduleHeight = this.size.height / matrix.height
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    drawRect(
                        color = actualColor,
                        topLeft = Offset(x * moduleWidth, y * moduleHeight),
                        size = Size(moduleWidth, moduleHeight),
                    )
                }
            }
        }
    }
}
