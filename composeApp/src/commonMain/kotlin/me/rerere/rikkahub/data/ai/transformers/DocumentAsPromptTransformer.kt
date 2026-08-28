package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.toLocalFilePath
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Formats that need a platform parser; everything else is read as plain text. */
private val PARSED_DOCUMENT_MIMES = setOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/epub+zip",
)

object DocumentAsPromptTransformer : InputMessageTransformer, KoinComponent {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val extractor = get<DocumentTextExtractor>()
        return withContext(Dispatchers.Default) {
            messages.map { message ->
                val documents = message.parts.filterIsInstance<UIMessagePart.Document>()
                if (documents.isEmpty()) return@map message
                message.copy(
                    parts = message.parts.toMutableList().apply {
                        documents.forEach { document ->
                            val content = readDocumentContent(document, extractor)
                            val path = resolveWorkspacePath(document)
                            val pathAttr = path?.let { " path=\"$it\"" } ?: ""
                            val prompt = """
                                  <UploadFile name="${document.fileName}"$pathAttr>
                                  ```
                                  $content
                                  ```
                                  </UploadFile>
                                  """.trimMargin()
                            add(0, UIMessagePart.Text(prompt))
                        }
                    }
                )
            }
        }
    }

    // 上传文件保存在 filesDir/upload 下, 该目录通过 proot 挂载到 workspace 的 /upload
    // 返回文件在 workspace 内的绝对路径, 便于 AI 用 workspace 工具直接读取原始文件
    private fun resolveWorkspacePath(document: UIMessagePart.Document): String? {
        val file = document.localFile() ?: return null
        if (file.parent()?.name != "upload") return null
        return "/upload/${file.name}"
    }

    private suspend fun readDocumentContent(
        document: UIMessagePart.Document,
        extractor: DocumentTextExtractor,
    ): String {
        val file = document.localFile()
            ?: return "[ERROR, invalid file uri: ${document.fileName}]"
        if (!file.exists() || !file.isRegularFile()) {
            return "[ERROR, file not found: ${document.fileName}]"
        }
        return runCatching {
            extractor.extract(file, document.mime)
                ?: if (document.mime in PARSED_DOCUMENT_MIMES) {
                    "[ERROR, ${document.mime} is not supported on this platform]"
                } else {
                    file.readString()
                }
        }.getOrElse {
            "[ERROR, failed to read file: ${document.fileName}]"
        }
    }

    private fun UIMessagePart.Document.localFile(): PlatformFile? =
        runCatching { PlatformFile(url.toLocalFilePath()) }.getOrNull()
}
