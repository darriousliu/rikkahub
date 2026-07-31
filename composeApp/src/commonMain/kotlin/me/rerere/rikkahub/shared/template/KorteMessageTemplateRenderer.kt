package me.rerere.rikkahub.shared.template

import korlibs.template.KorteAutoEscapeMode
import korlibs.template.KorteBlock
import korlibs.template.KorteExprNode
import korlibs.template.KorteFilter
import korlibs.template.KorteTag
import korlibs.template.KorteTemplate
import korlibs.template.KorteTemplateConfig
import korlibs.template.KorteTemplateProvider
import korlibs.template.KorteTemplates
import korlibs.template.expectEnd

class KorteMessageTemplateRenderer(
    templateSource: MessageTemplateSource,
    uppercase: (String) -> String,
    lowercase: (String) -> String,
) : MessageTemplateRenderer, TemplateCacheInvalidator {
    private val provider = object : KorteTemplateProvider {
        override suspend fun get(template: String): String? = templateSource.get(template)
    }

    private val templates = KorteTemplates(
        root = provider,
        config = KorteTemplateConfig(
            extraTags = listOf(pebbleForTag),
            extraFilters = pebbleFilters(uppercase, lowercase),
            autoEscapeMode = KorteAutoEscapeMode.RAW,
        ),
        cache = true,
    )

    override suspend fun get(templateName: String): MessageTemplate {
        val template = templates.get(templateName)
        return MessageTemplate { context -> template(context) }
    }

    override fun invalidateCache() {
        templates.invalidateCache()
    }
}

private val pebbleForTag = KorteTag(
    name = "for",
    nextList = setOf("else"),
    end = setOf("end", "endfor"),
) {
    val main = chunks.first()
    val elseBody = chunks.getOrNull(1)?.body
    val tokens = main.tag.tokens
    val variableName = KorteExprNode.parseId(tokens)
    KorteExprNode.expect(tokens, "in")
    val expression = KorteExprNode.parseExpr(tokens)
    tokens.expectEnd()

    object : KorteBlock {
        override suspend fun eval(context: KorteTemplate.EvalContext) {
            // Pebble skips both the loop and its else branch for missing or explicit null values.
            val evaluated = expression.eval(context) ?: return
            val items = evaluated.toPebbleLoopItems()
                ?: main.tag.posContext.exception("Not an iterable object. Value = [$evaluated]")

            if (items.isEmpty()) {
                elseBody?.eval(context)
                return
            }

            context.createScope {
                val loop = linkedMapOf<String, Any?>()
                context.scope.set("loop", loop)
                loop["length"] = items.size
                for ((index, item) in items.withIndex()) {
                    loop["index"] = index
                    loop["revindex"] = items.lastIndex - index
                    loop["first"] = index == 0
                    loop["last"] = index == items.lastIndex
                    context.scope.set(variableName, item)
                    main.body.eval(context)
                }
            }
        }
    }
}

private fun pebbleFilters(
    uppercase: (String) -> String,
    lowercase: (String) -> String,
): List<KorteFilter> = listOf(
    KorteFilter("upper") {
        subject?.let { uppercase(it.toString()) }
    },
    KorteFilter("lower") {
        subject?.let { lowercase(it.toString()) }
    },
    KorteFilter("capitalize") {
        subject?.let { capitalizeLikePebble(it as String) }
    },
    KorteFilter("default") {
        if (subject.isPebbleEmpty()) args.firstOrNull() else subject
    },
    KorteFilter("join") {
        val value = subject
        if (value == null) {
            null
        } else {
            val items = value.toPebbleJoinItems()
                ?: tok.exception("The 'join' filter expects a collection or array.")
            val separator = args.firstOrNull()?.let { it as String }
            items.joinToString(separator.orEmpty()) { it.toString() }
        }
    },
    KorteFilter("replace") {
        val value = subject
        if (value == null) {
            null
        } else {
            val replacements = args.firstOrNull() as? Map<*, *>
                ?: tok.exception("The argument 'replace_pairs' is required.")
            var result = value.toString()
            for ((from, to) in replacements) {
                result = result.replace(from.toString(), to.toString())
            }
            result
        }
    },
)

private fun capitalizeLikePebble(value: String): String {
    val index = value.indexOfFirst { !it.isWhitespace() }
    if (index < 0) return value
    return value.substring(0, index) +
        value[index].titlecaseChar() +
        value.substring(index + 1)
}

private fun Any?.isPebbleEmpty(): Boolean = when (this) {
    null -> true
    is String -> trim { it <= ' ' }.isEmpty()
    is Collection<*> -> isEmpty()
    is Map<*, *> -> isEmpty()
    else -> false
}

private fun Any.toPebbleLoopItems(): List<Any?>? = when (this) {
    is Map<*, *> -> entries.map { mapOf("key" to it.key, "value" to it.value) }
    is Iterable<*> -> toList()
    is Array<*> -> toList()
    is BooleanArray -> map { it }
    is ByteArray -> map { it }
    is ShortArray -> map { it }
    is IntArray -> map { it }
    is LongArray -> map { it }
    is FloatArray -> map { it }
    is DoubleArray -> map { it }
    is CharArray -> map { it }
    else -> null
}

private fun Any.toPebbleJoinItems(): List<Any?>? = when (this) {
    is Collection<*> -> toList()
    is Array<*> -> toList()
    is BooleanArray -> map { it }
    is ByteArray -> map { it }
    is ShortArray -> map { it }
    is IntArray -> map { it }
    is LongArray -> map { it }
    is FloatArray -> map { it }
    is DoubleArray -> map { it }
    is CharArray -> map { it }
    else -> null
}
