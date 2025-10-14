package me.rerere.rikkahub.utils

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.BitmapImage
import coil3.Uri
import coil3.asImage
import coil3.pathSegments
import coil3.util.MimeTypeMap
import io.github.vinceglb.filekit.utils.toByteArray
import io.github.vinceglb.filekit.utils.toNSData
import me.rerere.common.PlatformContext
import org.jetbrains.skia.Image
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

actual fun ByteArray.toImage(): BitmapImage {
    return try {
        val image = Image.makeFromEncoded(this)
        image.toComposeImageBitmap().asSkiaBitmap().asImage()
    } catch (e: Exception) {
        throw e
    }
}

actual fun BitmapImage.compress(): ByteArray {
    val skiaBitmap = this.bitmap
    val byteArray = skiaBitmap.readPixels() ?: return byteArrayOf()
    val uiImage = UIImage(byteArray.toNSData())
    val pngData = UIImagePNGRepresentation(uiImage)
    return pngData?.toByteArray() ?: byteArrayOf()
}

actual fun PlatformContext.getFileNameFromUri(uri: Uri): String? {
    return uri.pathSegments.lastOrNull()?.split(".")?.firstOrNull()
}

actual fun PlatformContext.getFileMimeType(uri: Uri): String? {
    return MimeTypeMap.getMimeTypeFromUrl(uri.toString())
}
