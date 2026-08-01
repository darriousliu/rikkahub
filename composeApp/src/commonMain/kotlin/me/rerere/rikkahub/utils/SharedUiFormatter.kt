package me.rerere.rikkahub.utils

import kotlinx.datetime.Month
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlin.time.Instant

internal expect object SharedUiFormatter {
    fun formatDateTime(epochMillis: Long, timeZoneId: String): String

    fun shortMonthName(monthNumber: Int): String

    fun formatDecimal(value: Double, fractionDigits: Int): String

    fun formatDate(year: Int, monthNumber: Int, day: Int, includeYear: Boolean): String
}

fun Instant.toLocalizedDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = SharedUiFormatter.formatDateTime(toEpochMilliseconds(), timeZone.id)

fun Month.toLocalizedShortString(): String = SharedUiFormatter.shortMonthName(number)

fun LocalDate.toLocalizedString(includeYear: Boolean): String =
    SharedUiFormatter.formatDate(year, month.number, day, includeYear)

fun Long.toLocalizedFileSize(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val fractionDigits = when {
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    return "${SharedUiFormatter.formatDecimal(value, fractionDigits)} ${units[unitIndex]}"
}
