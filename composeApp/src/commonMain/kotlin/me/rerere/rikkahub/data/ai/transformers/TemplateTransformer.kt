package me.rerere.rikkahub.data.ai.transformers

import korlibs.template.KorteTemplateProvider
import korlibs.template.KorteTemplates
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.toLocalizedDate
import me.rerere.rikkahub.utils.toLocalizedTime

class TemplateTransformer(
    private val engine: KorteTemplates,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val template = try {
            engine.get(ctx.assistant.id.toString())
        } catch (error: Throwable) {
            // Korte retains failed deferreds in its cache; Pebble only cached successful compilation.
            engine.invalidateCache()
            throw error
        }
        val timeZone = TimeZone.currentSystemDefault()
        return messages.map { message ->
            // 使用消息本身的发送时间而不是当前时间, 保证多次请求时渲染结果稳定, 不破坏 prompt 缓存
            val createdAt = message.createdAt.toInstant(timeZone)
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(
                                text = template(
                                    mapOf(
                                        "message" to part.text,
                                        "role" to message.role.name.lowercase(),
                                        "time" to createdAt.toLocalizedTime(includeSeconds = true),
                                        "date" to createdAt.toLocalizedDate(),
                                    )
                                )
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }
}

class AssistantTemplateLoader(private val settingsStore: SettingsStore) : KorteTemplateProvider {
    override suspend fun get(template: String): String? {
        return settingsStore.settingsFlow.value.assistants
            .find { it.id.toString() == template }?.messageTemplate
    }
}
