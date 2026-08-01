package me.rerere.rikkahub.utils

import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlin.time.Instant

internal expect object SharedUiFormatter {
    fun formatDateTime(epochMillis: Long, timeZoneId: String): String

    fun shortMonthName(monthNumber: Int): String

    fun formatDecimal(value: Double, fractionDigits: Int): String
}

fun Instant.toLocalizedDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = SharedUiFormatter.formatDateTime(toEpochMilliseconds(), timeZone.id)

fun Month.toLocalizedShortString(): String = SharedUiFormatter.shortMonthName(number)
