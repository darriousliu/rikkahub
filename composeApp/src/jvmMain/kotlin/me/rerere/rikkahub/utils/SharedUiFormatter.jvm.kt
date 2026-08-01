package me.rerere.rikkahub.utils

import java.time.Instant
import java.time.Month
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

internal actual object SharedUiFormatter {
    actual fun formatDateTime(epochMillis: Long, timeZoneId: String): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(timeZoneId)))

    actual fun shortMonthName(monthNumber: Int): String =
        Month.of(monthNumber).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    actual fun formatDecimal(value: Double, fractionDigits: Int): String =
        String.format(Locale.getDefault(), "%.${fractionDigits}f", value)

    actual fun formatDate(year: Int, monthNumber: Int, day: Int, includeYear: Boolean): String =
        localizedDateFormatter(includeYear).format(LocalDate.of(year, monthNumber, day))
}

private fun localizedDateFormatter(includeYear: Boolean): DateTimeFormatter {
    val locale = Locale.getDefault()
    if (includeYear) return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val monthFirst = locale.country in setOf("US", "PH", "CA", "CN")
    return DateTimeFormatterBuilder().apply {
        if (monthFirst) {
            appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            appendLiteral(' ')
            appendValue(ChronoField.DAY_OF_MONTH)
        } else {
            appendValue(ChronoField.DAY_OF_MONTH)
            appendLiteral(' ')
            appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
        }
    }.toFormatter(locale)
}
