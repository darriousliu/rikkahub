package me.rerere.rikkahub.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

actual fun formatLocalizedDate(date: LocalDate): String {
    val javaDate = date.toJavaLocalDate()
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(javaDate)
}

actual fun formatLocalizedDateTime(dateTime: LocalDateTime): String {
    val javaDateTime = dateTime.toJavaLocalDateTime()
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(javaDateTime)
}

actual fun formatLocalizedTime(dateTime: LocalDateTime): String {
    val javaTime = dateTime.time.toJavaLocalTime()
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(javaTime)
}

actual fun formatLocalizedDateWithoutYear(date: LocalDate): String {
    val locale = Locale.getDefault()
    val javaDate = date.toJavaLocalDate()

    val formatter = if (isMonthFirstLocale(locale)) {
        // Month-day format (e.g., "Sep 20" for US English)
        DateTimeFormatterBuilder()
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter(locale)
    } else {
        // Day-month format (e.g., "20 sep" for Swedish)
        DateTimeFormatterBuilder()
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(' ')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .toFormatter(locale)
    }

    return formatter.format(javaDate)
}

private fun isMonthFirstLocale(locale: Locale): Boolean {
    val monthFirstCountries = setOf(
        "US", // 美国
        "PH", // 菲律宾
        "CA", // 加拿大(虽然魁北克可能使用日-月格式)
        "CN", // 中国
    )
    return monthFirstCountries.contains(locale.country)
}

actual fun LocalDateTime.formatLocalizedTime(format: String): String {
    val javaDate = this.toJavaLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern(format).withLocale(Locale.getDefault())
    return formatter.format(javaDate)
}

actual fun DayOfWeek.localizedName(): String {
    val javaDow = java.time.DayOfWeek.of(this.isoDayNumber)
    return javaDow.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

actual fun parseRFC1123DateTime(dateTime: String): Instant {
    val zdt = ZonedDateTime.parse(dateTime, DateTimeFormatter.RFC_1123_DATE_TIME)
    return zdt.toInstant().toKotlinInstant()
}

actual fun parseRFC850DateTime(dateTime: String): Instant {
    val zdt = ZonedDateTime.parse(dateTime, DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz"))
    return zdt.toInstant().toKotlinInstant()
}
