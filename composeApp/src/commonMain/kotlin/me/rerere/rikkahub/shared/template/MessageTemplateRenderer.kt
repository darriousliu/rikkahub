package me.rerere.rikkahub.shared.template

fun interface MessageTemplate {
    suspend fun render(context: Map<String, Any?>): String
}

fun interface MessageTemplateSource {
    fun get(templateName: String): String?
}

interface MessageTemplateRenderer {
    suspend fun get(templateName: String): MessageTemplate
}

interface TemplateCacheInvalidator {
    fun invalidateCache()
}
