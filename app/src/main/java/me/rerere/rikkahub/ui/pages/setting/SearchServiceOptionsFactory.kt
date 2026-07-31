package me.rerere.rikkahub.ui.pages.setting

import me.rerere.search.SearchServiceOptions
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

internal fun createSearchServiceOptions(type: KClass<*>): SearchServiceOptions {
    if (type !in SearchServiceOptions.TYPES) {
        throw IllegalArgumentException("Unsupported search service options type: ${type.qualifiedName}")
    }
    return type.primaryConstructor?.callBy(emptyMap()) as? SearchServiceOptions
        ?: throw IllegalArgumentException("Unsupported search service options type: ${type.qualifiedName}")
}
