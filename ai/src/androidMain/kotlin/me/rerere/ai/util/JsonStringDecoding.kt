package me.rerere.ai.util

import org.apache.commons.text.StringEscapeUtils

internal fun decodeJsonEscapedString(value: String): String = StringEscapeUtils.unescapeJson(value)
