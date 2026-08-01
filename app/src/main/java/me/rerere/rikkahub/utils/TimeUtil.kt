package me.rerere.rikkahub.utils

import kotlinx.datetime.DayOfWeek as KotlinDayOfWeek
import kotlinx.datetime.LocalDate as KotlinLocalDate
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import me.rerere.common.time.today
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.time.Instant as JavaInstant
import kotlin.time.Clock
import kotlin.time.Instant as KotlinInstant

fun JavaInstant.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDateTime(toEpochMilli(), zoneId.id, locale)

fun KotlinInstant.toLocalDate(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDate(toEpochMilliseconds(), timeZone.id, locale)

fun KotlinInstant.toLocalDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDateTime(toEpochMilliseconds(), timeZone.id, locale)

fun KotlinInstant.toLocalTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatTime(toEpochMilliseconds(), timeZone.id, locale)

fun KotlinLocalDate.toLocalString(
    includeYear: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    return LocalDate.of(year, month.number, day).toLocalString(includeYear, locale)
}

fun KotlinLocalDateTime.toLocalString(
    locale: Locale = Locale.getDefault(),
): String = toPlatformLocalDateTime().toLocalString(locale)

fun KotlinLocalDateTime.toMessageTimeString(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    return if (date == clock.today(timeZone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(toPlatformLocalDateTime())
    } else {
        toLocalString(locale)
    }
}

fun KotlinDayOfWeek.toLocalString(
    locale: Locale = Locale.getDefault(),
): String = java.time.DayOfWeek.valueOf(name).getDisplayName(TextStyle.FULL, locale)

internal object PlatformTimeFormatter {
    fun formatDate(epochMillis: Long, timeZoneId: String, locale: Locale): String {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(localDateTime(epochMillis, timeZoneId))
    }

    fun formatDateTime(epochMillis: Long, timeZoneId: String, locale: Locale): String {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(localDateTime(epochMillis, timeZoneId))
    }

    fun formatTime(epochMillis: Long, timeZoneId: String, locale: Locale): String {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(localDateTime(epochMillis, timeZoneId))
    }

    private fun localDateTime(epochMillis: Long, timeZoneId: String): LocalDateTime {
        return JavaInstant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.of(timeZoneId))
            .toLocalDateTime()
    }
}

fun LocalDateTime.toLocalString(
    locale: Locale = Locale.getDefault(),
): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    return formatter.format(this)
}

fun LocalDate.toLocalString(
    includeYear: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    val formatter = if (includeYear) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    } else {
        if (isMonthFirstLocale(locale)) {
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
    }

    return formatter.format(this)
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

private fun KotlinLocalDateTime.toPlatformLocalDateTime(): LocalDateTime {
    return LocalDateTime.of(year, month.number, day, hour, minute, second, nanosecond)
}
