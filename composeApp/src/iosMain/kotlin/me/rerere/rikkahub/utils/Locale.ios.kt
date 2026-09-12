package me.rerere.rikkahub.utils

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageCode

internal actual fun languagePromptCode(languageTag: String): String = languageTag.replace('-', '_')
internal actual fun englishLanguageName(languageTag: String): String =
    NSLocale(localeIdentifier = "en").displayNameForKey(NSLocaleLanguageCode, languageTag.substringBefore('-'))
        ?: languageTag
internal actual val Throwable.platformClassName: String get() = this::class.simpleName ?: "Throwable"
