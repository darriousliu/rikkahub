package me.rerere.search

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal actual fun currentSearchLocale(): SearchLocale {
    val components = NSLocale.currentLocale.localeIdentifier
        .replace('_', '-')
        .split('-')
    return SearchLocale(
        language = components.firstOrNull().orEmpty(),
        country = components.drop(1).firstOrNull { component -> component.length == 2 }.orEmpty(),
    )
}
