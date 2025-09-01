package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.PlatformContext
import me.rerere.ai.ui.UIMessagePart

internal actual fun openDocument(
    context: PlatformContext,
    document: UIMessagePart.Document
) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.data = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        document.url.toUri().toFile()
    )
    val chooserIndent = Intent.createChooser(intent, null)
    context.startActivity(chooserIndent)
}
