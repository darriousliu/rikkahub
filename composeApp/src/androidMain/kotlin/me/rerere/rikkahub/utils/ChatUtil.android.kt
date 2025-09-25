package me.rerere.rikkahub.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import coil3.BitmapImage
import coil3.Uri
import coil3.asImage
import coil3.toAndroidUri
import coil3.toCoilUri
import me.rerere.common.PlatformContext
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.ExperimentalEncodingApi
import android.net.Uri as AndroidUri

actual fun ByteArray.toImage(): BitmapImage = BitmapFactory.decodeByteArray(this, 0, this.size).asImage()

actual fun BitmapImage.compress(): ByteArray = ByteArrayOutputStream().use {
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    it.toByteArray()
}

@OptIn(ExperimentalEncodingApi::class)
@JvmName("createChatFilesByContentsJvm")
fun PlatformContext.createChatFilesByContents(uris: List<AndroidUri>): List<AndroidUri> {
    return createChatFilesByContents(uris.map { it.toCoilUri() }).map { it.toAndroidUri() }
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
