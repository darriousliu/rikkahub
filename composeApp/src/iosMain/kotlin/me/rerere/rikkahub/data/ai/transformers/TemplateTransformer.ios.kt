package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.utils.toLocalTime
import kotlin.time.Clock

actual class TemplateTransformer(
    private val settingsStore: SettingsStore
) : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(
                                text = applyTemplate(assistant.messageTemplate, part.text, message)
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }

    private fun applyTemplate(template: String, text: String, message: UIMessage): String {
        val regex = Regex("\\{\\{(.+?)}}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1].trim()
            when (key) {
                "message" -> text
                "role" -> message.role.name.lowercase()
                "time" -> Clock.System.now().toLocalTime()
                "date" -> Clock.System.now().toLocalTime()
                else -> matchResult.value
            }
        }
    }
}
