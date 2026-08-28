package me.rerere.rikkahub.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal expect object SharedUiFormatter {
    fun formatDateTime(epochMillis: Long, timeZoneId: String): String

    fun formatTime(epochMillis: Long, timeZoneId: String, includeSeconds: Boolean): String

    fun shortMonthName(monthNumber: Int): String

    /** [isoDayNumber] follows ISO-8601: 1 is Monday and 7 is Sunday. */
    fun dayOfWeekName(isoDayNumber: Int): String

    fun formatDecimal(value: Double, fractionDigits: Int): String

    fun formatDate(year: Int, monthNumber: Int, day: Int, includeYear: Boolean): String
}

fun Instant.toLocalizedDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = SharedUiFormatter.formatDateTime(toEpochMilliseconds(), timeZone.id)

fun LocalDateTime.toLocalizedDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = toInstant(timeZone).toLocalizedDateTime(timeZone)

fun LocalDateTime.toMessageTimeString(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = if (date == clock.now().toLocalDateTime(timeZone).date) {
    SharedUiFormatter.formatTime(toInstant(timeZone).toEpochMilliseconds(), timeZone.id, includeSeconds = false)
} else {
    toLocalizedDateTime(timeZone)
}

fun Instant.toLocalizedTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    includeSeconds: Boolean = false,
): String = SharedUiFormatter.formatTime(toEpochMilliseconds(), timeZone.id, includeSeconds)

fun Instant.toLocalizedDate(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    includeYear: Boolean = true,
): String = toLocalDateTime(timeZone).date.toLocalizedString(includeYear)

fun DayOfWeek.toLocalizedName(): String = SharedUiFormatter.dayOfWeekName(isoDayNumber)

fun Month.toLocalizedShortString(): String = SharedUiFormatter.shortMonthName(number)

fun LocalDate.toLocalizedString(includeYear: Boolean): String =
    SharedUiFormatter.formatDate(year, month.number, day, includeYear)

fun Float.toLocalizedDecimal(fractionDigits: Int): String =
    SharedUiFormatter.formatDecimal(toDouble(), fractionDigits)

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
