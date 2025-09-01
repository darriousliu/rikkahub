package me.rerere.rikkahub.ui.components.ai

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coil3.Uri
import coil3.compose.LocalPlatformContext
import coil3.toCoilUri
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Files
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import com.mohamedrejeb.calf.permissions.Permission
import com.mohamedrejeb.calf.permissions.isGranted
import com.mohamedrejeb.calf.permissions.rememberPermissionState
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.toFile
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*
import java.io.File
import kotlin.uuid.Uuid
import android.net.Uri as AndroidUri

@Composable
private fun useCropLauncher(
    onCroppedImageReady: (AndroidUri) -> Unit,
    onCleanup: (() -> Unit)? = null
): Pair<ActivityResultLauncher<Intent>, (AndroidUri) -> Unit> {
    val context = LocalPlatformContext.current
    var cropOutputUri by remember { mutableStateOf<AndroidUri?>(null) }

    val cropActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cropOutputUri?.let { croppedUri ->
                onCroppedImageReady(croppedUri)
            }
        }
        // Clean up crop output file
        cropOutputUri?.toFile()?.delete()
        cropOutputUri = null
        onCleanup?.invoke()
    }

    val launchCrop: (AndroidUri) -> Unit = { sourceUri ->
        val outputFile = File(context.appTempFolder.toFile(), "crop_output_${System.currentTimeMillis()}.jpg")
        cropOutputUri = outputFile.toUri()

        val cropIntent = UCrop.of(sourceUri, cropOutputUri!!)
            .withOptions(UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setAllowedGestures(
                    UCropActivity.SCALE,
                    UCropActivity.ROTATE,
                    UCropActivity.NONE
                )
                setCompressionFormat(Bitmap.CompressFormat.PNG)
            })
            .withMaxResultSize(4096, 4096)
            .getIntent(context)

        cropActivityLauncher.launch(cropIntent)
    }

    return Pair(cropActivityLauncher, launchCrop)
}

@Composable
internal actual fun ImagePickButton(onAddImages: (List<Uri>) -> Unit) {
    val context = LocalPlatformContext.current
    val settings = LocalSettings.current

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri.toCoilUri())))
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            Logger.d("ImagePickButton") { "Selected URIs: $selectedUris" }
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add images
                onAddImages(context.createChatFilesByContents(selectedUris.map { it.toCoilUri() }))
            } else {
                // Show crop interface
                if (selectedUris.size == 1) {
                    // Single image - offer crop
                    launchCrop(selectedUris.first())
                } else {
                    // Multiple images - no crop
                    onAddImages(context.createChatFilesByContents(selectedUris.map { it.toCoilUri() }))
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
        imagePickerLauncher.launch("image/*")
    }
}

@Composable
actual fun TakePicButton(onAddImages: (List<Uri>) -> Unit) {
    val cameraPermission = rememberPermissionState(Permission.Camera)

    val context = LocalPlatformContext.current
    val settings = LocalSettings.current
    var cameraOutputUri by remember { mutableStateOf<AndroidUri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri.toCoilUri())))
        },
        onCleanup = {
            // Clean up camera temp file after cropping is done
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add image
                onAddImages(context.createChatFilesByContents(listOf(cameraOutputUri!!.toCoilUri())))
                // Clean up camera temp file
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            } else {
                // Show crop interface
                launchCrop(cameraOutputUri!!)
            }
        } else {
            // Clean up camera temp file if capture failed
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }

    // 使用权限管理器包装
    BigIconTextButton(
        icon = {
            Icon(Lucide.Camera, null)
        },
        text = {
            Text(stringResource(Res.string.take_picture))
        }
    ) {
        if (cameraPermission.status.isGranted) {
            // 权限已授权，直接启动相机
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            // 请求权限
            cameraPermission.launchPermissionRequest()
        }
    }
}

@Composable
actual fun FilePickButton(onAddFiles: (List<UIMessagePart.Document>) -> Unit) {
    val context = LocalPlatformContext.current
    val toaster = LocalToaster.current
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
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

                val documents = uris.mapNotNull { uri ->
                    val fileName = context.getFileNameFromUri(uri) ?: "file"
                    val mime = context.getFileMimeType(uri) ?: "text/plain"

                    // Filter by MIME type or file extension
                    val isAllowed = allowedMimeTypes.contains(mime) ||
                        mime.startsWith("text/") ||
                        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        mime == "application/pdf" ||
                        fileName.endsWith(".txt", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".csv", ignoreCase = true) ||
                        fileName.endsWith(".json", ignoreCase = true) ||
                        fileName.endsWith(".js", ignoreCase = true) ||
                        fileName.endsWith(".html", ignoreCase = true) ||
                        fileName.endsWith(".css", ignoreCase = true) ||
                        fileName.endsWith(".xml", ignoreCase = true) ||
                        fileName.endsWith(".py", ignoreCase = true) ||
                        fileName.endsWith(".java", ignoreCase = true) ||
                        fileName.endsWith(".kt", ignoreCase = true) ||
                        fileName.endsWith(".ts", ignoreCase = true) ||
                        fileName.endsWith(".tsx", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".markdown", ignoreCase = true) ||
                        fileName.endsWith(".mdx", ignoreCase = true) ||
                        fileName.endsWith(".yml", ignoreCase = true) ||
                        fileName.endsWith(".yaml", ignoreCase = true)

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
        pickMedia.launch(arrayOf("*/*"))
    }
}

@Stable
internal actual fun provideDialogProperties(): DialogProperties {
    return DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    )
}

internal actual fun createImageReceiveListener(
    state: ChatInputState,
    context: PlatformContext
): ReceiveContentListener {
    return ReceiveContentListener { transferableContent ->
        when {
            transferableContent.hasMediaType(MediaType.Image) -> {
                transferableContent.consume { item ->
                    val uri = item.uri
                    if (uri != null) {
                        state.addImages(
                            context.createChatFilesByContents(listOf(uri.toCoilUri()))
                        )
                    }
                    uri != null
                }
            }

            else -> transferableContent
        }
    }
}

internal actual fun Modifier.platformContentReceiver(listener: ReceiveContentListener): Modifier {
    return this.then(Modifier.contentReceiver(listener))
}
