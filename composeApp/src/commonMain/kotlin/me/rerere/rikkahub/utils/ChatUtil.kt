package me.rerere.rikkahub.utils

import androidx.navigation.NavHostController
import co.touchlab.kermit.Logger
import coil3.BitmapImage
import coil3.Uri
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.common.utils.createNewFile
import me.rerere.common.utils.delete
import me.rerere.common.utils.deleteRecursively
import me.rerere.common.utils.mkdirs
import me.rerere.common.utils.toUri
import me.rerere.rikkahub.Screen
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

private const val TAG = "ChatUtil"

fun navigateToChatPage(
    navController: NavHostController,
    chatId: Uuid = Uuid.random(),
    initText: String? = null,
    initFiles: List<Uri> = emptyList(),
) {
    Logger.i(TAG) { "navigateToChatPage: navigate to $chatId" }
    navController.navigate(
        route = Screen.Chat(
            id = chatId.toString(),
            text = initText,
            files = initFiles.map { it.toString() },
        ),
    ) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun PlatformContext.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toText())
}

expect fun ByteArray.toImage(): BitmapImage

expect fun BitmapImage.compress(): ByteArray

fun PlatformContext.createChatFilesByContents(uris: List<Uri>): List<Uri> {
    val newUris = mutableListOf<Uri>()

    val dir = FileKit.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    uris.forEach { uri ->
        val fileName = Uuid.random()
        val file = dir.resolve("$fileName")
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri(this)
        runCatching {
            val inputFile = PlatformFile(uri.toString())
            runBlocking { file.write(inputFile) }
            newUris.add(newUri)
        }.onFailure {
            it.printStackTrace()
            Logger.e(TAG, it) { "createChatFilesByContents: Failed to save image from $uri" }
        }
    }
    return newUris
}

suspend fun PlatformContext.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = FileKit.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    byteArrays.forEach { byteArray ->
        val fileName = Uuid.random()
        val file = dir.resolve("$fileName")
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri(this)
        file.write(byteArray)
        newUris.add(newUri)
    }
    return newUris
}

expect fun PlatformContext.getFileNameFromUri(uri: Uri): String?

expect fun PlatformContext.getFileMimeType(uri: Uri): String?

suspend fun PlatformContext.saveMessageImage(image: String) = withContext(Dispatchers.IO) {
    when {
        image.startsWith("data:image") -> {
            val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
            val bitmap = byteArray.toImage()
            exportImage(this@saveMessageImage, bitmap)
        }

        image.startsWith("file:") -> {
            val file = PlatformFile(image)
            exportImageFile(this@saveMessageImage, file)
        }

        image.startsWith("http") -> {
            kotlin.runCatching { // Use runCatching to handle potential network exceptions
                val httpClient = HttpClient()

                val response = httpClient.get(image)

                if (response.status.isSuccess()) {
                    val bitmap = response.bodyAsBytes().toImage()
                    exportImage(this@saveMessageImage, bitmap)
                } else {
                    Logger.e(TAG) { "saveMessageImage: Failed to download image from $image, response code: ${response.status.value}" }
                    null // Return null on failure
                }
            }.getOrNull() // Return null if any exception occurs during download
        }

        else -> error("Invalid image format")
    }
}

suspend fun PlatformContext.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
    withContext(Dispatchers.IO) {
        message.copy(
            parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Image -> {
                        if (part.url.startsWith("data:image")) {
                            // base64 image
                            val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                            val bitmap = sourceByteArray.toImage()
                            val byteArray = bitmap.compress()
                            val urls = createChatFilesByByteArrays(listOf(byteArray))
                            Logger.i(TAG) {
                                "convertBase64ImagePartToLocalFile: convert base64 img to ${
                                    urls.joinToString(
                                        ", "
                                    )
                                }"
                            }
                            part.copy(
                                url = urls.first().toString(),
                            )
                        } else {
                            part
                        }
                    }

                    else -> part
                }
            }
        )
    }

fun PlatformContext.deleteChatFiles(uris: List<Uri>) {
    uris.filter { it.toString().startsWith("file:") }.forEach { uri ->
        val file = PlatformFile(uri.toString())
        if (file.exists()) {
            file.delete()
        }
    }
}

fun PlatformContext.deleteAllChatFiles() {
    val dir = FileKit.filesDir.resolve("upload")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
}

suspend fun PlatformContext.countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val dir = FileKit.filesDir.resolve("upload")
    if (!dir.exists()) {
        return@withContext Pair(0, 0)
    }
    val files = dir.list()
    val count = files.size
    val size = files.sumOf { it.size() }
    Pair(count, size)
}

fun PlatformContext.getImagesDir(): PlatformFile {
    val dir = FileKit.filesDir.resolve("images")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

suspend fun PlatformContext.createImageFileFromBase64(base64Data: String, filePath: String): PlatformFile =
    withContext(Dispatchers.IO) {
        val data = if (base64Data.startsWith("data:image")) {
            base64Data.substringAfter("base64,")
        } else {
            base64Data
        }

        val byteArray = Base64.decode(data.toByteArray())
        val file = PlatformFile(filePath)
        file.parent()?.mkdirs()
        file.write(byteArray)
        return@withContext file
    }

fun PlatformContext.listImageFiles(): List<PlatformFile> {
    val imagesDir = getImagesDir()
    return imagesDir.list()
        .filter { it.isRegularFile() && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }.toList()
}
