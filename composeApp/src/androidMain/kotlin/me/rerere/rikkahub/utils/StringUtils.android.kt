package me.rerere.rikkahub.utils

import org.apache.commons.text.StringEscapeUtils

actual fun String.escapeHtml(): String {
    return StringEscapeUtils.escapeHtml4(this)
}

actual fun String.unescapeHtml(): String {
    return StringEscapeUtils.unescapeHtml4(this)
}
