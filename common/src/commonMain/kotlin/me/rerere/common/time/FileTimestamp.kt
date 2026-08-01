package me.rerere.common.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Instant.toCompactFileTimestamp(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = toLocalDateTime(timeZone)
    return buildString(15) {
        appendFixed(local.year, 4)
        appendFixed(local.month.number, 2)
        appendFixed(local.day, 2)
        append('_')
        appendFixed(local.hour, 2)
        appendFixed(local.minute, 2)
        appendFixed(local.second, 2)
    }
}

fun Instant.toDashedFileTimestamp(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = toLocalDateTime(timeZone)
    return buildString(19) {
        appendFixed(local.year, 4)
        append('-')
        appendFixed(local.month.number, 2)
        append('-')
        appendFixed(local.day, 2)
        append('_')
        appendFixed(local.hour, 2)
        append('-')
        appendFixed(local.minute, 2)
        append('-')
        appendFixed(local.second, 2)
    }
}

private fun StringBuilder.appendFixed(value: Int, width: Int) {
    append(value.toString().padStart(width, '0'))
}
