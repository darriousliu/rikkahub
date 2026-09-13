package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import me.rerere.common.logging.RikkaLog as Log
import java.io.ByteArrayOutputStream
import java.io.File

object FileUtils {
    private const val TAG = "FileUtils"

    fun buildRelativePath(folder: String, file: File): String =
        "$folder/${file.name}"

    fun getRelativePathInFilesDir(filesDir: File, file: File): String? {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val canonicalFilesDir = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val basePath = canonicalFilesDir.path
        val filePath = canonicalFile.path
        if (!filePath.startsWith("$basePath${File.separator}")) {
            return null
        }
        return canonicalFile.relativeTo(canonicalFilesDir).path.replace(File.separatorChar, '/')
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            var fileName: String? = null
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val documentDisplayNameIndex =
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (documentDisplayNameIndex != -1) {
                        fileName = cursor.getString(documentDisplayNameIndex)
                    } else {
                        val openableDisplayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (openableDisplayNameIndex != -1) {
                            fileName = cursor.getString(openableDisplayNameIndex)
                        }
                    }
                }
            }
            fileName
        }.onFailure {
            Log.w(TAG, "getFileNameFromUri: Failed to query display name for $uri", it)
        }.getOrNull()
    }

    fun getFileMimeType(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> runCatching {
                context.contentResolver.getType(uri)
            }.onFailure {
                Log.w(TAG, "getFileMimeType: Failed to resolve MIME for $uri", it)
            }.getOrNull()
            else -> null
        }
    }

    fun guessMimeType(file: File, fileName: String): String =
        me.rerere.rikkahub.data.files.guessMimeType(kotlinx.io.files.Path(file.path), fileName)

    fun compressBitmapToPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

}
