package me.rerere.rikkahub.data.files

import kotlin.uuid.Uuid

fun buildUuidFileName(displayName: String?, mimeType: String?): String {
    val extFromName = displayName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it != displayName }
        ?.lowercase()
    val extFromMime = mimeType
        ?.let { extensionFromMimeType(it.lowercase()) }
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
    val ext = extFromName ?: extFromMime ?: "bin"
    return "${Uuid.random()}.$ext"
}

internal expect fun extensionFromMimeType(mimeType: String): String?
