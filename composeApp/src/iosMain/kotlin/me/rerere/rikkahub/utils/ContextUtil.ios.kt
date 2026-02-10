package me.rerere.rikkahub.utils

import co.touchlab.kermit.Logger
import coil3.BitmapImage
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.utils.toNSData
import me.rerere.common.PlatformContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIPasteboard

private const val TAG = "ContextUtil"

actual fun PlatformContext.readClipboardText(): String {
    return UIPasteboard.generalPasteboard.string ?: ""
}

actual fun PlatformContext.joinQQGroup(key: String?): Boolean {
    val urlString =
        "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key"
    val url = NSURL(string = urlString)
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    return true
}

actual fun PlatformContext.writeClipboardText(text: String) {
    runCatching {
        UIPasteboard.generalPasteboard.string = text
        Logger.i(TAG) { "writeClipboardText: $text" }
    }.onFailure {
        Logger.e(TAG, it) { "writeClipboardText failed" }
    }
}

actual fun PlatformContext.openUrl(url: String) {
    Logger.i(TAG) { "openUrl: $url" }
    runCatching {
        val nsUrl = NSURL(string = url)
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }.onFailure {
        Logger.e(TAG, it) { "Failed to open URL: $url" }
    }
}

actual fun PlatformContext.exportImage(
    activity: PlatformContext,
    bitmap: BitmapImage,
    fileName: String
) {
    runCatching {
        val skiaBitmap = bitmap.bitmap
        val skiaImage = Image.makeFromBitmap(skiaBitmap)
        val pngBytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw IllegalStateException("Failed to encode bitmap to PNG")
        val nsData = pngBytes.toNSData()
        val uiImage = UIImage.imageWithData(nsData)
            ?: throw IllegalStateException("Failed to create UIImage from PNG data")
        UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
        Logger.i(TAG) { "Image saved successfully: $fileName" }
    }.onFailure {
        Logger.e(TAG, it) { "Failed to save image" }
    }
}

actual fun PlatformContext.exportImageFile(
    activity: PlatformContext,
    file: PlatformFile,
    fileName: String
) {
    runCatching {
        val nsUrl = file.nsUrl
        val data = NSData.dataWithContentsOfURL(nsUrl)
            ?: throw IllegalStateException("Failed to read file data from: ${nsUrl.path}")
        val uiImage = UIImage.imageWithData(data)
            ?: throw IllegalStateException("Failed to create UIImage from file data")
        UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
        Logger.i(TAG) { "Image file saved successfully: $fileName" }
    }.onFailure {
        Logger.e(TAG, it) { "Failed to save image file" }
    }
}
