package me.rerere.rikkahub.data.files

import android.webkit.MimeTypeMap

internal actual fun extensionFromMimeType(mimeType: String): String? =
    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
