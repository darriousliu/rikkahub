package me.rerere.rikkahub.data.ai.transformers

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager

/**
 * Uses [FilesManager] so stored images stay in the managed-file registry that backs the
 * Android files page.
 */
class AndroidBase64ImageStore(
    private val filesManager: FilesManager,
) : Base64ImageStore {
    override suspend fun storeAsPng(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val png = FileUtils.compressBitmapToPng(bitmap)
        filesManager.createChatFilesByByteArrays(listOf(png)).firstOrNull()?.toString()
    }
}
