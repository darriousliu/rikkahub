package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.viewinterop.UIKitView
import me.rerere.rikkahub.utils.QRCodeEncoder
import me.rerere.rikkahub.utils.toCssHex
import org.koin.compose.koinInject
import platform.UIKit.UIImageView

@Composable
actual fun QRCode(
    value: String,
    modifier: Modifier,
    size: Int,
    color: Color,
    backgroundColor: Color
) {
    val actualColor = color.takeOrElse { MaterialTheme.colorScheme.secondary }
    val actualBackgroundColor = backgroundColor.takeOrElse { MaterialTheme.colorScheme.surface }
    val qrCodeEncoder = koinInject<QRCodeEncoder>()
    UIKitView(
        factory = {
            val uiImage = qrCodeEncoder.encode(value, size, actualColor.toCssHex(), actualBackgroundColor.toCssHex())
            UIImageView(image = uiImage)
        },
        modifier = modifier,
    )
}
