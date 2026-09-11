package me.rerere.rikkahub.data.files

import io.ktor.http.ContentType
import io.ktor.http.fileExtensions

internal actual fun extensionFromMimeType(mimeType: String): String? =
    runCatching { ContentType.parse(mimeType).fileExtensions().firstOrNull() }.getOrNull()
