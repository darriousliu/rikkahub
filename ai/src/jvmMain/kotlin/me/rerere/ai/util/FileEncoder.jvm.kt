@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package me.rerere.ai.util

import java.io.File
import java.net.URI
import kotlin.io.encoding.Base64

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
    val file = if (pathOrUrl.startsWith("file://")) File(URI(pathOrUrl)) else File(pathOrUrl)
    require(file.exists()) { "File does not exist: $pathOrUrl" }
    return LocalFilePayload(
        name = file.name,
        extension = file.extension.lowercase(),
        bytes = file.readBytes(),
    )
}

private fun imageMimeType(extension: String): String = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "image/png"
}
