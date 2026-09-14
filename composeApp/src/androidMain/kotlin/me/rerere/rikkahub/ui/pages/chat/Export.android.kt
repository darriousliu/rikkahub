package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.common.android.appTempFolder
import me.rerere.common.time.toDashedFileTimestamp
import me.rerere.rikkahub.shared.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.BitmapComposer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.getActivity
import java.io.FileOutputStream
import kotlin.time.Clock

internal actual suspend fun exportToImage(
    context: Context,
    compositionLocalContext: CompositionLocalContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions
) {
    val filename = "chat-export-${Clock.System.now().toDashedFileTimestamp()}.png"
    val composer = BitmapComposer(scope)
    val activity = context.getActivity()
    if (activity == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to get activity", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val bitmap = composer.composableToBitmap(
        activity = activity,
        width = 540.dp,
        screenDensity = density,
        content = {
            CompositionLocalProvider(LocalSettings provides settings) {
                ExportedChatImage(
                    conversation = conversation,
                    messages = messages,
                    options = options
                )
            }
        }
    )

    try {
        val dir = context.appTempFolder
        val file = dir.resolve(filename)
        if (!file.exists()) {
            file.createNewFile()
        } else {
            file.delete()
            file.createNewFile()
        }

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
        }

        // Save to gallery
        context.exportImage(activity, bitmap, filename)

        // Share the file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareFile(context, uri, "image/png")
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to export image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        bitmap.recycle()
    }
}


private fun shareFile(context: Context, uri: Uri, mimeType: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent,
            context.getString(R.string.chat_page_export_share_via)
        )
    )
}

internal actual fun ImageRequest.Builder.disableHardwareBitmaps(): ImageRequest.Builder = allowHardware(false)
