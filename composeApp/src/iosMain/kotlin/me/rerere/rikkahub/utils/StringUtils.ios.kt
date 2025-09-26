package me.rerere.rikkahub.utils

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface HtmlEscaper {
    fun escapeHtml(input: String): String
    fun unescapeHtml(input: String): String
}

private object DefaultHtmlEscaper : KoinComponent {
    val iosEscaper by inject<HtmlEscaper>()
}

actual fun String.escapeHtml(): String {
    return DefaultHtmlEscaper.iosEscaper.escapeHtml(this)
}

actual fun String.unescapeHtml(): String {
    return DefaultHtmlEscaper.iosEscaper.unescapeHtml(this)
}


