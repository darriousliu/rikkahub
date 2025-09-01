package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.Uri
import coil3.asImage
import coil3.toAndroidUri
import coil3.toCoilUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.toFile
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.BitmapComposer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.toLocalString
import org.jetbrains.compose.resources.getString
import rikkahub.composeapp.generated.resources.*
import java.io.FileOutputStream
import kotlin.io.resolve
import kotlin.time.Clock
import kotlin.use

internal actual suspend fun exportToImage(
    context: PlatformContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions
) {
    val filename =
        "chat-export-${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toLocalString()}.png"
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
        val dir = context.appTempFolder.toFile()
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
        context.exportImage(activity, bitmap.asImage(), filename)

        // Share the file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareFile(context, uri.toCoilUri(), "image/png")
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to export image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        bitmap.recycle()
    }
}

internal actual fun shareFile(context: PlatformContext, uri: Uri, mimeType: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri.toAndroidUri())
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent,
            runBlocking { getString(Res.string.chat_page_export_share_via) }
        )
    )
}
