package me.rerere.rikkahub.utils

import java.util.Locale

internal actual fun languagePromptCode(languageTag: String): String = Locale.forLanguageTag(languageTag).toString()
internal actual fun englishLanguageName(languageTag: String): String =
    Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.ENGLISH)
internal actual val Throwable.platformClassName: String get() = javaClass.name
