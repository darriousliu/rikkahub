package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.PlatformContext
import me.rerere.ai.ui.UIMessagePart

internal actual fun openDocument(
    context: PlatformContext,
    part: UIMessagePart
) {
    val url = when (part) {
        is UIMessagePart.Video -> part.url
        is UIMessagePart.Audio -> part.url
        is UIMessagePart.Document -> part.url
        else -> return
    }
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.data = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        url.toUri().toFile()
    )
    val chooserIndent = Intent.createChooser(intent, null)
    context.startActivity(chooserIndent)
}
