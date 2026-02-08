package me.rerere.rikkahub.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Instant

actual fun formatLocalizedDate(date: LocalDate): String {
    val nsDate = date.toNSDateComponents().date ?: NSDate()
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        locale = NSLocale.currentLocale()
    }
    return formatter.stringFromDate(nsDate)
}

actual fun formatLocalizedDateTime(dateTime: LocalDateTime): String {
    val nsDate = dateTime.toNSDateComponents().date ?: NSDate()
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterMediumStyle
        locale = NSLocale.currentLocale()
    }
    return formatter.stringFromDate(nsDate)
}

actual fun formatLocalizedTime(dateTime: LocalDateTime): String {
    val nsDate = dateTime.toNSDateComponents().date ?: NSDate()
    val formatter = NSDateFormatter().apply {
        timeStyle = NSDateFormatterMediumStyle
        locale = NSLocale.currentLocale()
    }
    return formatter.stringFromDate(nsDate)
}

actual fun formatLocalizedDateWithoutYear(date: LocalDate): String {
    val nsDate = date.toNSDateComponents().date ?: NSDate()
    val locale = NSLocale.currentLocale()

    val formatter = NSDateFormatter().apply {
        this.locale = locale
        dateFormat = if (isMonthFirstLocale(locale)) {
            // Month-day format
            "MMM d"
        } else {
            // Day-month format
            "d MMM"
        }
    }
    return formatter.stringFromDate(nsDate)
}

private fun isMonthFirstLocale(locale: NSLocale): Boolean {
    val countryCode = locale.countryCode
    val monthFirstCountries = setOf(
        "US", // 美国
        "PH", // 菲律宾
        "CA", // 加拿大
        "CN", // 中国
    )
    return monthFirstCountries.contains(countryCode)
}

actual fun LocalDateTime.formatLocalizedTime(format: String): String {
    val nsDate = this.toNSDateComponents().date ?: NSDate()
    val formatter = NSDateFormatter().apply {
        dateFormat = format
        locale = NSLocale.currentLocale()
    }
    return formatter.stringFromDate(nsDate)
}

actual fun DayOfWeek.localizedName(): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
    }
    // standaloneWeekdaySymbols 索引: 0=Sunday, 1=Monday, ...
    val symbols = formatter.standaloneWeekdaySymbols.map { it as String }
    val sundayBasedIndex = if (this.isoDayNumber == 7) 0 else this.isoDayNumber
    return symbols[sundayBasedIndex]
}

actual fun parseRFC1123DateTime(dateTime: String): Instant {
    NSDateFormatter().apply {
        dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    }.let { formatter ->
        val nsDate = formatter.dateFromString(dateTime) ?: NSDate()
        val timeInterval = nsDate.timeIntervalSince1970.toLong()
        return Instant.fromEpochMilliseconds(timeInterval)
    }
}

actual fun parseRFC850DateTime(dateTime: String): Instant {

    NSDateFormatter().apply {
        dateFormat = "EEEE, dd-MMM-yy HH:mm:ss zzz"
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    }.let { formatter ->
        val nsDate = formatter.dateFromString(dateTime) ?: NSDate()
        val timeInterval = nsDate.timeIntervalSince1970.toLong()
        return Instant.fromEpochMilliseconds(timeInterval)
    }
}
