package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import coil3.Image
import coil3.asImage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

@Composable
actual fun DrawableResource.toCoilImage(): Image {
    return imageResource(this).asAndroidBitmap().asImage(shareable = true)
}
