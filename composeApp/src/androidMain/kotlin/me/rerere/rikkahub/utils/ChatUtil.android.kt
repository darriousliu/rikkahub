package me.rerere.rikkahub.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import co.touchlab.kermit.Logger
import coil3.*
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.common.utils.toPlatformFile
import me.rerere.rikkahub.Screen
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid
import android.net.Uri as AndroidUri

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

actual fun PlatformContext.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toText())
}

actual fun ByteArray.toImage(): BitmapImage = BitmapFactory.decodeByteArray(this, 0, this.size).asImage()

@OptIn(ExperimentalEncodingApi::class)
@JvmName("createChatFilesByContentsJvm")
fun PlatformContext.createChatFilesByContents(uris: List<AndroidUri>): List<AndroidUri> {
    return createChatFilesByContents(uris.map { it.toCoilUri() }).map { it.toAndroidUri() }
}

actual fun PlatformContext.createChatFilesByContents(uris: List<Uri>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    uris.forEach { uri ->
        val fileName = Uuid.random()
        val file = dir.resolve("$fileName")
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri().toCoilUri()
        runCatching {
            this.contentResolver.openInputStream(uri.toAndroidUri())?.use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            newUris.add(newUri)
        }.onFailure {
            it.printStackTrace()
            Logger.e(TAG, it) { "createChatFilesByContents: Failed to save image from $uri" }
        }
    }
    return newUris
}

actual fun PlatformContext.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    byteArrays.forEach { byteArray ->
        val fileName = Uuid.random()
        val file = dir.resolve("$fileName")
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri().toCoilUri()
        file.outputStream().use { outputStream ->
            outputStream.write(byteArray)
        }
        newUris.add(newUri)
    }
    return newUris
}

fun PlatformContext.getFileNameFromUri(uri: AndroidUri): String? {
    return getFileNameFromUri(uri.toCoilUri())
}

actual fun PlatformContext.getFileNameFromUri(uri: Uri): String? {
    var fileName: String? = null
    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME // 优先尝试 DocumentProvider 标准列
    )
    contentResolver.query(uri.toAndroidUri(), projection, null, null, null)?.use { cursor ->
        // 移动到第一行结果
        if (cursor.moveToFirst()) {
            // 尝试获取 DocumentsContract.Document.COLUMN_DISPLAY_NAME 的索引
            val documentDisplayNameIndex =
                cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (documentDisplayNameIndex != -1) {
                fileName = cursor.getString(documentDisplayNameIndex)
            } else {
                // 如果 DocumentProvider 标准列不存在，尝试 OpenableColumns.DISPLAY_NAME
                val openableDisplayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (openableDisplayNameIndex != -1) {
                    fileName = cursor.getString(openableDisplayNameIndex)
                }
            }
        }
    }
    // 如果查询失败或没有获取到名称，fileName 会保持 null
    return fileName
}

fun PlatformContext.getFileMimeType(uri: AndroidUri): String? {
    return getFileMimeType(uri.toCoilUri())
}

actual fun PlatformContext.getFileMimeType(uri: Uri): String? {
    return when (uri.scheme) {
        "content" -> contentResolver.getType(uri.toAndroidUri())
        else -> null
    }
}

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun PlatformContext.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
    withContext(Dispatchers.IO) {
        message.copy(
            parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Image -> {
                        if (part.url.startsWith("data:image")) {
                            // base64 image
                            val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                            val bitmap =
                                BitmapFactory.decodeByteArray(sourceByteArray, 0, sourceByteArray.size).asImage()
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

actual fun BitmapImage.compress(): ByteArray = ByteArrayOutputStream().use {
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    it.toByteArray()
}

@JvmName("deleteChatFilesJvm")
fun PlatformContext.deleteChatFiles(uris: List<AndroidUri>) {
    deleteChatFiles(uris.map { it.toCoilUri() })
}

actual fun PlatformContext.deleteChatFiles(uris: List<Uri>) {
    uris.filter { it.toString().startsWith("file:") }.forEach { uri ->
        val file = uri.toAndroidUri().toFile()
        if (file.exists()) {
            file.delete()
        }
    }
}

actual fun PlatformContext.deleteAllChatFiles() {
    val dir = this.filesDir.resolve("upload")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
}

actual suspend fun PlatformContext.countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val dir = filesDir.resolve("upload")
    if (!dir.exists()) {
        return@withContext Pair(0, 0)
    }
    val files = dir.listFiles() ?: return@withContext Pair(0, 0)
    val count = files.size
    val size = files.sumOf { it.length() }
    Pair(count, size)
}

actual fun PlatformContext.getImagesDir(): PlatformFile {
    val dir = this.filesDir.resolve("images")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir.toPlatformFile()
}

actual fun PlatformContext.createImageFileFromBase64(base64Data: String, filePath: String): PlatformFile {
    val data = if (base64Data.startsWith("data:image")) {
        base64Data.substringAfter("base64,")
    } else {
        base64Data
    }

    val byteArray = Base64.decode(data.toByteArray())
    val file = File(filePath)
    file.parentFile?.mkdirs()
    file.writeBytes(byteArray)
    return file.toPlatformFile()
}
