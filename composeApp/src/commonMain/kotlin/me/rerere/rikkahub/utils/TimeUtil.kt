package me.rerere.rikkahub.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Instant.toLocalDate(): String {
    val timeZone = TimeZone.currentSystemDefault()
    val localDateTime = this.toLocalDateTime(timeZone)
    return formatLocalizedDate(localDateTime.date)
}

fun Instant.toLocalDateTime(): String {
    val timeZone = TimeZone.currentSystemDefault()
    val localDateTime = this.toLocalDateTime(timeZone)
    return formatLocalizedDateTime(localDateTime)
}

fun Instant.toLocalTime(): String {
    val timeZone = TimeZone.currentSystemDefault()
    val localDateTime = this.toLocalDateTime(timeZone)
    return formatLocalizedTime(localDateTime)
}

fun LocalDateTime.toLocalString(): String {
    return formatLocalizedDateTime(this)
}

fun LocalDate.toLocalString(includeYear: Boolean): String {
    return if (includeYear) {
        formatLocalizedDate(this)
    } else {
        formatLocalizedDateWithoutYear(this)
    }
}

expect fun formatLocalizedDate(date: LocalDate): String
expect fun formatLocalizedDateTime(dateTime: LocalDateTime): String
expect fun formatLocalizedTime(dateTime: LocalDateTime): String
expect fun formatLocalizedDateWithoutYear(date: LocalDate): String

expect fun LocalDateTime.formatLocalizedTime(format: String): String

expect fun DayOfWeek.localizedName(): String

expect fun parseRFC1123DateTime(dateTime: String): Instant

expect fun parseRFC850DateTime(dateTime: String): Instant
