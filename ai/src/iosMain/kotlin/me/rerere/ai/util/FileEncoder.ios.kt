@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package me.rerere.ai.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.io.encoding.Base64
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.memcpy

internal actual fun encodeLocalImageFile(fileUrl: String, withPrefix: Boolean): EncodedImage {
    val payload = readLocalFile(fileUrl)
    val mimeType = imageMimeType(payload.extension)
    val encoded = Base64.encode(payload.bytes)
    return EncodedImage(
        base64 = if (withPrefix) "data:$mimeType;base64,$encoded" else encoded,
        mimeType = mimeType,
    )
}

internal actual fun encodeLocalMediaFile(
    fileUrl: String,
    mimeType: String,
    withPrefix: Boolean,
): String {
    val encoded = Base64.encode(readLocalFile(fileUrl).bytes)
    return if (withPrefix) "data:$mimeType;base64,$encoded" else encoded
}

internal actual fun readLocalFile(pathOrUrl: String): LocalFilePayload {
    val path = if (pathOrUrl.startsWith("file://")) {
        NSURL.URLWithString(pathOrUrl)?.path
            ?: throw IllegalArgumentException("Invalid file URI: $pathOrUrl")
    } else {
        pathOrUrl
    }
    val data = NSFileManager.defaultManager.contentsAtPath(path)
        ?: throw IllegalArgumentException("File does not exist: $pathOrUrl")
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
    val name = path.substringAfterLast('/')
    return LocalFilePayload(
        name = name,
        extension = name.substringAfterLast('.', "").lowercase(),
        bytes = bytes,
    )
}

private fun imageMimeType(extension: String): String = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "image/png"
}
