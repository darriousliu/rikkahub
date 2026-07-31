package me.rerere.rikkahub.shared.template

fun interface MessageTemplate {
    suspend fun render(context: Map<String, Any?>): String
}

interface MessageTemplateRenderer {
    suspend fun get(templateName: String): MessageTemplate
}

interface TemplateCacheInvalidator {
    fun invalidateCache()
}
