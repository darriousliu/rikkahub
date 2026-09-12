package me.rerere.common.text

/** Formats fixed decimal places using the current locale, as Java's %f formatter does. */
expect fun formatFixedDecimal(value: Double, fractionDigits: Int): String
