package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.shared.template.KorteMessageTemplateRenderer
import me.rerere.rikkahub.shared.template.MessageTemplate
import me.rerere.rikkahub.shared.template.MessageTemplateRenderer
import me.rerere.rikkahub.shared.template.MessageTemplateSource
import me.rerere.rikkahub.shared.template.TemplateCacheInvalidator
import me.rerere.rikkahub.utils.toLocalizedDate
import me.rerere.rikkahub.utils.toLocalizedTime

class TemplateTransformer(
    private val renderer: MessageTemplateRenderer,
    private val contextFactory: MessageTemplateContextFactory = MessageTemplateContextFactory(),
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val template = renderer.get(ctx.assistant.id.toString())
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(
                                text = template.render(contextFactory.create(message, part.text))
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }
}

class MessageTemplateContextFactory(
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun create(message: UIMessage, text: String): Map<String, Any?> {
        // 使用消息本身的发送时间而不是当前时间, 保证多次请求时渲染结果稳定, 不破坏 prompt 缓存
        val timeZone = timeZoneProvider()
        val createdAt = message.createdAt.toInstant(timeZone)
        return mapOf(
            "message" to text,
            "role" to message.role.name.lowercase(),
            "time" to createdAt.toLocalizedTime(timeZone, includeSeconds = true),
            "date" to createdAt.toLocalizedDate(timeZone),
        )
    }
}

class DefaultMessageTemplateRenderer(
    templateSource: MessageTemplateSource,
) : MessageTemplateRenderer, TemplateCacheInvalidator {
    private val delegate = KorteMessageTemplateRenderer(
        templateSource = templateSource,
        uppercase = String::uppercase,
        lowercase = String::lowercase,
    )

    override suspend fun get(templateName: String): MessageTemplate = delegate.get(templateName)

    override fun invalidateCache() {
        delegate.invalidateCache()
    }
}
