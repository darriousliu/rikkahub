package me.rerere.rikkahub.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.time.Instant

internal actual object SharedUiFormatter {
    actual fun formatDateTime(epochMillis: Long, timeZoneId: String): String {
        val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.of(timeZoneId))
        return buildString {
            append(dateTime.year)
            append('-')
            append(dateTime.month.number.toString().padStart(2, '0'))
            append('-')
            append(dateTime.day.toString().padStart(2, '0'))
            append(' ')
            append(dateTime.hour.toString().padStart(2, '0'))
            append(':')
            append(dateTime.minute.toString().padStart(2, '0'))
            append(':')
            append(dateTime.second.toString().padStart(2, '0'))
        }
    }

    actual fun shortMonthName(monthNumber: Int): String = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    ).getOrElse(monthNumber - 1) { monthNumber.toString() }

    actual fun formatDecimal(value: Double, fractionDigits: Int): String {
        val factor = when (fractionDigits) {
            0 -> 1L
            1 -> 10L
            else -> 100L
        }
        val scaled = (value * factor).roundToLong()
        if (fractionDigits == 0) return scaled.toString()
        val fraction = (scaled % factor).absoluteValue.toString().padStart(fractionDigits, '0')
        return "${scaled / factor}.$fraction"
    }
}
