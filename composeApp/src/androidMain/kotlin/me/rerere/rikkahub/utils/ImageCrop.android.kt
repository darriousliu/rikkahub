package me.rerere.rikkahub.utils

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import me.rerere.common.android.appTempFolder
import java.io.File

internal actual fun prepareImageForCrop(source: PlatformFile): PlatformFile? {
    val context = FileKit.context
    val tempFile = File(context.appTempFolder, "pick_temp_${System.currentTimeMillis()}.jpg")
    val uri = source.toAndroidUri()
    // HEIF/HEIC（尤其 HDR HEIF）交给 UCrop 前先解码转为 JPEG，规避裁剪解码失败
    val converted = ImageUtils.isHeifImage(context, uri) &&
        ImageUtils.convertHeifToJpeg(context, uri, tempFile)
    if (!converted) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return PlatformFile(tempFile)
}
