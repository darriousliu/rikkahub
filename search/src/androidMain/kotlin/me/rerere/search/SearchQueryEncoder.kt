package me.rerere.search

internal fun encodeSearchQuery(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
