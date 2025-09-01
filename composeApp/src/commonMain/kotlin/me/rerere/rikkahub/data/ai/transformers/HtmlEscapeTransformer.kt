package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.unescapeHtml

object HtmlEscapeTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>
    ): List<UIMessage> {
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (message.role == MessageRole.ASSISTANT && part is UIMessagePart.Text) {
                        UIMessagePart.Text(part.text.unescapeHtml())
                    } else {
                        part
                    }
                }
            )
        }
    }
}
