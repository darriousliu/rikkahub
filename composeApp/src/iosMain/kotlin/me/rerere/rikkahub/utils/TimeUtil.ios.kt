package me.rerere.rikkahub.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.*

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
