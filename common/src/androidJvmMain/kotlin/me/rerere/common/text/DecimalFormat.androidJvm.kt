package me.rerere.common.text

import java.util.Locale

actual fun formatFixedDecimal(value: Double, fractionDigits: Int): String =
    String.format(Locale.getDefault(), "%.${fractionDigits}f", value)
