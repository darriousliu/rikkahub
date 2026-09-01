package me.rerere.rikkahub.utils

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.numberWithDouble
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

internal actual object SharedUiFormatter {
    actual fun formatDateTime(epochMillis: Long, timeZoneId: String): String =
        dateFormatter(timeZoneId) {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterMediumStyle
        }.stringFromDate(epochMillis.toNSDate())

    actual fun formatTime(epochMillis: Long, timeZoneId: String, includeSeconds: Boolean): String =
        dateFormatter(timeZoneId) {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = if (includeSeconds) NSDateFormatterMediumStyle else NSDateFormatterShortStyle
        }.stringFromDate(epochMillis.toNSDate())

    actual fun shortMonthName(monthNumber: Int): String {
        val symbols = NSDateFormatter().apply { locale = NSLocale.currentLocale }.shortMonthSymbols
        return symbols.getOrNull(monthNumber - 1) as? String ?: monthNumber.toString()
    }

    actual fun dayOfWeekName(isoDayNumber: Int): String {
        // NSDateFormatter starts its week on Sunday, ISO-8601 starts on Monday.
        val symbols = NSDateFormatter().apply { locale = NSLocale.currentLocale }.weekdaySymbols
        return symbols.getOrNull(isoDayNumber % 7) as? String ?: isoDayNumber.toString()
    }

    actual fun formatDecimal(value: Double, fractionDigits: Int): String {
        val formatter = NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = fractionDigits.toULong()
            maximumFractionDigits = fractionDigits.toULong()
            usesGroupingSeparator = false
        }
        return formatter.stringFromNumber(NSNumber.numberWithDouble(value)) ?: value.toString()
    }

    actual fun formatDate(year: Int, monthNumber: Int, day: Int, includeYear: Boolean): String {
        val date = NSCalendar.currentCalendar.dateFromComponents(
            NSDateComponents().apply {
                setYear(year.toLong())
                setMonth(monthNumber.toLong())
                setDay(day.toLong())
            },
        ) ?: return "$year-$monthNumber-$day"
        val formatter = NSDateFormatter().apply {
            locale = NSLocale.currentLocale
            if (includeYear) {
                dateStyle = NSDateFormatterMediumStyle
                timeStyle = NSDateFormatterNoStyle
            } else {
                // Lets the locale decide between "Sep 20" and "20 Sep".
                setLocalizedDateFormatFromTemplate("MMMd")
            }
        }
        return formatter.stringFromDate(date)
    }
}

private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / 1000.0)

private fun dateFormatter(
    timeZoneId: String,
    configure: NSDateFormatter.() -> Unit,
): NSDateFormatter = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.timeZoneWithName(timeZoneId) ?: NSTimeZone.localTimeZone
    configure()
}
