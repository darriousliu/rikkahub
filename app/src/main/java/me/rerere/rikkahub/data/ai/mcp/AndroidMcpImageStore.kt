package me.rerere.rikkahub.data.ai.mcp

import android.webkit.MimeTypeMap
import me.rerere.rikkahub.data.files.toFileUri
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes

class AndroidMcpImageStore(
    private val filesManager: FilesManager,
) : McpImageStore {
    override suspend fun save(bytes: ByteArray, mimeType: String): UIMessagePart.Image {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        val entity = filesManager.saveUploadFromBytes(
            bytes = bytes,
            displayName = "mcp_image.$extension",
            mimeType = mimeType,
        )
        return UIMessagePart.Image(url = filesManager.getFile(entity).toFileUri())
    }
}
