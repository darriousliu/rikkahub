package me.rerere.common.text

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale
import platform.Foundation.numberWithDouble

actual fun formatFixedDecimal(value: Double, fractionDigits: Int): String {
    if (!value.isFinite()) return value.toString()
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        usesGroupingSeparator = false
        roundingMode = NSNumberFormatterRoundHalfUp
    }
    return formatter.stringFromNumber(NSNumber.numberWithDouble(value))!!
}
