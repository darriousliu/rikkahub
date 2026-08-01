package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.rerere.rikkahub.platform.PlatformQrCodeRenderer

@Composable
fun QRCode(
    value: String,
    modifier: Modifier = Modifier,
    size: Int = 512,
    color: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified
) {
    val actualColor = color.takeOrElse { MaterialTheme.colorScheme.secondary }
    val actualBackgroundColor = backgroundColor.takeOrElse { Color.Transparent }

    val renderer = remember { PlatformQrCodeRenderer() }
    val matrix = remember(value, size) {
        renderer.render(content = value, size = size).getOrThrow()
    }
    val bitmap = remember(matrix, actualColor, actualBackgroundColor) {
        createBitmap(matrix.width, matrix.height).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    this[x, y] = if (matrix[x, y]) actualColor.toArgb() else actualBackgroundColor.toArgb()
                }
            }
        }
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "qrcode:$value",
        modifier = modifier
    )
}
