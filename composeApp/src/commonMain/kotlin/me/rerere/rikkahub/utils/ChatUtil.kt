package me.rerere.rikkahub.utils

import co.touchlab.kermit.Logger
import coil3.BitmapImage
import coil3.Uri
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.common.PlatformContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "ChatUtil"

expect fun PlatformContext.copyMessageToClipboard(message: UIMessage)
expect fun ByteArray.toImage(): BitmapImage


@OptIn(markerClass = [ExperimentalEncodingApi::class])
expect fun PlatformContext.createChatFilesByContents(uris: List<Uri>): List<Uri>

expect fun PlatformContext.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri>

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

expect fun BitmapImage.compress(): ByteArray

@OptIn(markerClass = [ExperimentalEncodingApi::class])
expect suspend fun PlatformContext.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage

expect fun PlatformContext.deleteChatFiles(uris: List<Uri>)

expect fun PlatformContext.deleteAllChatFiles()

expect suspend fun PlatformContext.countChatFiles(): Pair<Int, Long>

expect fun PlatformContext.getImagesDir(): PlatformFile

expect fun PlatformContext.createImageFileFromBase64(base64Data: String, filePath: String): PlatformFile

fun PlatformContext.listImageFiles(): List<PlatformFile> {
    val imagesDir = getImagesDir()
    return imagesDir.list()
        .filter { it.isRegularFile() && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }.toList()
}
