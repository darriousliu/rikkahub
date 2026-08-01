package me.rerere.rikkahub.utils

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
import kotlin.time.Instant as KotlinInstant

fun JavaInstant.toLocalDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDate(toEpochMilli(), zoneId.id, locale)

fun JavaInstant.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDateTime(toEpochMilli(), zoneId.id, locale)

fun JavaInstant.toLocalTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatTime(toEpochMilli(), zoneId.id, locale)

fun KotlinInstant.toLocalDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDate(toEpochMilliseconds(), zoneId.id, locale)

fun KotlinInstant.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatDateTime(toEpochMilliseconds(), zoneId.id, locale)

fun KotlinInstant.toLocalTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = PlatformTimeFormatter.formatTime(toEpochMilliseconds(), zoneId.id, locale)

fun KotlinInstant.toPlatformLocalDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate = PlatformTimeFormatter.localDate(toEpochMilliseconds(), zoneId.id)

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

    fun localDate(epochMillis: Long, timeZoneId: String): LocalDate {
        return localDateTime(epochMillis, timeZoneId).toLocalDate()
    }

    private fun localDateTime(epochMillis: Long, timeZoneId: String): LocalDateTime {
        return JavaInstant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.of(timeZoneId))
            .toLocalDateTime()
    }
}

fun LocalDateTime.toLocalString(): String {
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    return formatter.format(this)
}

/**
 * 消息时间显示：当天只显示时间（如 14:30），非当天显示「月日 + 时间」（如 5月20日 14:30）。
 */
fun LocalDateTime.toMessageTimeString(): String {
    val locale = Locale.getDefault()
    return if (this.toLocalDate() == LocalDate.now()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(this)
    } else {
        this.toLocalString()
    }
}

fun LocalDate.toLocalString(includeYear: Boolean): String {
    val locale = Locale.getDefault()
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
