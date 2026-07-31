package me.rerere.rikkahub.data.ai.transformers

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.Loader
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.shared.template.MessageTemplate
import me.rerere.rikkahub.shared.template.MessageTemplateRenderer
import me.rerere.rikkahub.shared.template.MessageTemplateSource
import me.rerere.rikkahub.shared.template.TemplateCacheInvalidator
import me.rerere.rikkahub.utils.toLocalDate
import me.rerere.rikkahub.utils.toLocalTime
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toJavaInstant

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
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val localeProvider: () -> Locale = { Locale.getDefault() },
) {
    fun create(message: UIMessage, text: String): Map<String, Any?> {
        // 使用消息本身的发送时间而不是当前时间, 保证多次请求时渲染结果稳定, 不破坏 prompt 缓存
        val createdAt = message.createdAt.toInstant(timeZoneProvider()).toJavaInstant()
        val zoneId = zoneIdProvider()
        val locale = localeProvider()
        return mapOf(
            "message" to text,
            "role" to message.role.name.lowercase(),
            "time" to createdAt.toLocalTime(zoneId, locale),
            "date" to createdAt.toLocalDate(zoneId, locale),
        )
    }
}

class DefaultMessageTemplateRenderer(
    templateSource: MessageTemplateSource,
    locale: Locale = Locale.getDefault(),
) : MessageTemplateRenderer, TemplateCacheInvalidator {
    private val engine = PebbleEngine.Builder()
        .loader(MessageTemplateLoader(templateSource))
        .defaultLocale(locale)
        .autoEscaping(false)
        .build()

    override suspend fun get(templateName: String): MessageTemplate {
        val template = engine.getTemplate(templateName)
        return MessageTemplate { context ->
            val result = StringWriter()
            template.evaluate(result, context)
            result.toString()
        }
    }

    override fun invalidateCache() {
        engine.templateCache.invalidateAll()
    }
}

private class MessageTemplateLoader(
    private val templateSource: MessageTemplateSource,
) : Loader<String> {
    override fun getReader(cacheKey: String?): Reader? = cacheKey
        ?.let(templateSource::get)
        ?.let(::StringReader)

    override fun setCharset(charset: String?) {}

    override fun setPrefix(prefix: String?) {}

    override fun setSuffix(suffix: String?) {}

    override fun resolveRelativePath(
        relativePath: String?,
        anchorPath: String?
    ): String? {
        return relativePath
    }

    override fun createCacheKey(templateName: String?): String? {
        return templateName
    }

    override fun resourceExists(templateName: String?): Boolean {
        return templateName?.let(templateSource::get) != null
    }
}
