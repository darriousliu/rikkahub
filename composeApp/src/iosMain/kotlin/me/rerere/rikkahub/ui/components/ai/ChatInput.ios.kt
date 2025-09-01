package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import coil3.Uri
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.Files
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.common.utils.toUri
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*

@Composable
internal actual fun ImagePickButton(onAddImages: (List<Uri>) -> Unit) {
    val context = LocalPlatformContext.current
    val settings = LocalSettings.current

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Multiple()
    ) { selectedUris ->
        if (selectedUris?.isNotEmpty() == true) {
            Logger.d("ImagePickButton") { "Selected URIs: $selectedUris" }
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add images
                onAddImages(context.createChatFilesByContents(selectedUris.map { it.toUri(context) }))
            } else {
                // Show crop interface
                if (selectedUris.size == 1) {
                    // Single image - offer crop
                    // TODO implement crop
//                    launchCrop(selectedUris.first())
                    onAddImages(context.createChatFilesByContents(selectedUris.map { it.toUri(context) }))
                } else {
                    // Multiple images - no crop
                    onAddImages(context.createChatFilesByContents(selectedUris.map { it.toUri(context) }))
                }
            }
        } else {
            Logger.d("ImagePickButton") { "No images selected" }
        }
    }

    BigIconTextButton(
        icon = {
            Icon(Lucide.Image, null)
        },
        text = {
            Text(stringResource(Res.string.photo))
        }
    ) {
        imagePickerLauncher.launch()
    }
}

@Composable
actual fun TakePicButton(onAddImages: (List<Uri>) -> Unit) {
    // TODO
}

@Composable
actual fun FilePickButton(onAddFiles: (List<UIMessagePart.Document>) -> Unit) {
    val context = LocalPlatformContext.current
    val toaster = LocalToaster.current
    val pickMedia = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Multiple()
    ) { files ->
        if (files?.isNotEmpty() == true) {
            val allowedMimeTypes = setOf(
                "text/plain",
                "text/html",
                "text/css",
                "text/javascript",
                "text/csv",
                "text/xml",
                "application/json",
                "application/javascript",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )

            val documents = files.mapNotNull { file ->
                val uri = file.toUri(context)
                val fileName = context.getFileNameFromUri(uri) ?: "file"
                val mime = context.getFileMimeType(uri) ?: "text/plain"
                val fileExtRegex = Regex(
                    """\.(txt|md|csv|json|js|html|css|xml|py|java|kt|ts|tsx|markdown|mdx|yml|yaml)$""",
                    RegexOption.IGNORE_CASE
                )

                // Filter by MIME type or file extension
                val isAllowed = allowedMimeTypes.contains(mime) ||
                    mime.startsWith("text/") ||
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    mime == "application/pdf" ||
                    fileExtRegex.containsMatchIn(fileName)

                if (isAllowed) {
                    val localUri = context.createChatFilesByContents(listOf(uri))[0]
                    UIMessagePart.Document(
                        url = localUri.toString(),
                        fileName = fileName,
                        mime = mime
                    )
                } else {
                    null
                }
            }

            if (documents.isNotEmpty()) {
                onAddFiles(documents)
            } else {
                // Show toast for unsupported file types
                toaster.show("不支持的文件类型", type = ToastType.Error)
            }
        }
    }
    BigIconTextButton(
        icon = {
            Icon(Lucide.Files, null)
        },
        text = {
            Text(stringResource(Res.string.upload_file))
        }
    ) {
        pickMedia.launch()
    }
}

@Stable
internal actual fun provideDialogProperties(): DialogProperties {
    return DialogProperties(
        usePlatformDefaultWidth = false,
    )
}

internal actual fun createImageReceiveListener(
    state: ChatInputState,
    context: PlatformContext
): ReceiveContentListener {
    return ReceiveContentListener { it }
}

internal actual fun Modifier.platformContentReceiver(listener: ReceiveContentListener): Modifier {
    return this
}
