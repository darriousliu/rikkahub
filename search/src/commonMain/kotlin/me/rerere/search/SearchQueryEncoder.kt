package me.rerere.search

import io.ktor.http.encodeURLQueryComponent

internal fun encodeSearchQuery(value: String): String =
    value.encodeURLQueryComponent(
        encodeFull = true,
        spaceToPlus = true,
    )
        .replace("%2D", "-")
        .replace("%2E", ".")
        .replace("%5F", "_")
        .replace("%2A", "*")
