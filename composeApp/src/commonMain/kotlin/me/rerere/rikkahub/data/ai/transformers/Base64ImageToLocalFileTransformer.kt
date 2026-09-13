package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.files.FilesManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object Base64ImageToLocalFileTransformer : OutputMessageTransformer, KoinComponent {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val filesManager = get<FilesManager>()
        return messages.map { message ->
            filesManager.convertBase64ImagePartToLocalFile(message)
        }
    }
}
