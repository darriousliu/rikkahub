package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.DocumentReader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual object DocumentAsPromptTransformer : InputMessageTransformer, KoinComponent {
    private val documentReader by inject<DocumentReader>()

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return withContext(Dispatchers.IO) {
            messages.map { message ->
                message.copy(
                    parts = message.parts.toMutableList().apply {
                        val documents = filterIsInstance<UIMessagePart.Document>()
                        if (documents.isNotEmpty()) {
                            documents.forEach { document ->
                                val file = PlatformFile(document.url)
                                val content = when (document.mime) {
                                    "application/pdf" -> documentReader.extractPdfText(document.url).orEmpty()
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                                        documentReader.extractDocxText(
                                            document.url
                                        ).orEmpty()

                                    else -> file.readString()
                                }
                                val prompt = """
                  ## user sent a file: ${document.fileName}
                  <content>
                  ```
                  $content
                  ```
                  </content>
                  """.trimMargin()
                                add(0, UIMessagePart.Text(prompt))
                            }
                        }
                    }
                )
            }
        }
    }
}
