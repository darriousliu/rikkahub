package me.rerere.rikkahub.ui.pages.setting

import me.rerere.search.SearchProviderRegistry
import me.rerere.search.SearchServiceOptions
import kotlin.reflect.KClass

internal fun createSearchServiceOptions(type: KClass<*>): SearchServiceOptions =
    SearchProviderRegistry.createOptions(type)
