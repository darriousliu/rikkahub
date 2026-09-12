package me.rerere.rikkahub.utils

internal expect fun languagePromptCode(languageTag: String): String
internal expect fun englishLanguageName(languageTag: String): String
internal expect val Throwable.platformClassName: String
