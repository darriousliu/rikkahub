package me.rerere.rikkahub.utils

import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
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
}
