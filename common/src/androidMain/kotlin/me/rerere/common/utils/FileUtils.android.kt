@file:Suppress("NOTHING_TO_INLINE")

package me.rerere.common.utils

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import co.touchlab.kermit.Logger
import coil3.Uri
import coil3.toAndroidUri
import coil3.toCoilUri
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import me.rerere.common.PlatformContext
import java.io.File
import kotlin.io.deleteRecursively
import kotlin.io.readBytes
import kotlin.use
import org.koin.mp.KoinPlatform

private const val TAG = "FileUtils"

inline fun File.toPlatformFile() = PlatformFile(this)

/**
 * 从文件选择器中得到的PlatformFile以content://开头，禁止使用
 */
inline fun PlatformFile.toFile() = androidFile.let {
    when (it) {
        is AndroidFile.FileWrapper -> it.file
        is AndroidFile.UriWrapper -> it.uri.toFile()
    }
}

private fun PlatformFile.isContentUri(): Boolean =
    androidFile is AndroidFile.UriWrapper && this.path.startsWith("content://")


actual fun getUriForFile(
    context: PlatformContext,
    file: PlatformFile
): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.toFile()).toCoilUri()
}

actual fun PlatformFile.deleteRecursively(): Boolean {
    return if (isContentUri()) {
        val uri = (androidFile as AndroidFile.UriWrapper).uri
        try {
            val context = KoinPlatform.getKoin().get<Context>() as Context
            val resolver = context.contentResolver
            // 方案 1: 针对 DocumentsProvider (SAF 选择的文件通常是这种)
            // 检查是否支持 DocumentsContract
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return DocumentsContract.deleteDocument(resolver, uri).also {
                    if (it) Logger.d(TAG) { "DocumentsContract 删除成功: $uri" }
                }
            }
            // 方案 2: 针对 MediaStore (如相册/音频) 或其他 ContentProvider
            // ContentResolver.delete 返回受影响的行数，>0 表示有动作
            val rowsDeleted = resolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                Logger.d(TAG) { "ContentResolver 删除成功: $uri" }
                return true
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, e) { "删除失败: 没有权限 (请检查是否使用了 ACTION_OPEN_DOCUMENT)" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "删除失败: 未知错误" }
        }
        true
    } else {
        toFile().deleteRecursively()
    }
}


actual fun PlatformFile.toUri(context: PlatformContext): Uri {
    return if (isContentUri()) {
        (androidFile as AndroidFile.UriWrapper).uri.toCoilUri()
    } else {
        getUriForFile(context, this)
    }
}

/**
 * 必须以file://开头
 */
actual fun Uri.toFile(): PlatformFile {
    return toAndroidUri().toFile().toPlatformFile()
}

actual fun PlatformFile.canRead(): Boolean {
    return toFile().canRead()
}

actual fun writeContentToUri(
    context: PlatformContext,
    uri: Uri,
    content: ByteArray
) {
    context.contentResolver.openOutputStream(uri.toAndroidUri())?.use { output ->
        output.write(content)
    }
}

actual fun readContentFromUri(context: PlatformContext, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri.toAndroidUri())
        ?.use { it.readBytes() }
        ?: error("Failed to read file")
}

