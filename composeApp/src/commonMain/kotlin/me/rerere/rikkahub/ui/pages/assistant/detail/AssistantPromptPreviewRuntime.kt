package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.shared.template.KorteMessageTemplateRenderer
import me.rerere.rikkahub.shared.template.MessageTemplateSource

fun interface AssistantPromptPreviewRuntime {
    suspend fun transform(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
    ): List<UIMessage>
}

object CommonAssistantPromptPreviewRuntime : AssistantPromptPreviewRuntime {
    override suspend fun transform(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val renderer = KorteMessageTemplateRenderer(
            templateSource = MessageTemplateSource { assistant.messageTemplate },
            uppercase = String::uppercase,
            lowercase = String::lowercase,
        )
        val template = renderer.get(assistant.id.toString())
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = template.render(
                                mapOf(
                                    "message" to part.text,
                                    "role" to message.role.name.lowercase(),
                                    "time" to message.createdAt.time.toString(),
                                    "date" to message.createdAt.date.toString(),
                                ),
                            ),
                        )
                    } else {
                        part
                    }
                },
            )
        }
    }
}
