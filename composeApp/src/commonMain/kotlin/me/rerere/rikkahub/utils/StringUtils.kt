package me.rerere.rikkahub.utils

import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLQueryComponent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
    return encodeURLQueryComponent(
        encodeFull = true,
        spaceToPlus = true,
    )
        .replace("%2D", "-")
        .replace("%2E", ".")
        .replace("%5F", "_")
        .replace("%2A", "*")
}

fun String.urlDecode(): String {
    return try {
        decodeURLQueryComponent(plusIsSpace = true)
    } catch (error: URLDecodeException) {
        throw IllegalArgumentException(error.message, error)
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String = Base64.encode(encodeToByteArray())

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String = Base64.decode(this).decodeToString()

fun Number.toFixed(digits: Int = 0): String =
    SharedUiFormatter.formatDecimal(toDouble(), digits)

fun String.applyPlaceholders(
    vararg placeholders: Pair<String, String>,
): String {
    var result = this
    for ((placeholder, replacement) in placeholders) {
        result = result.replace("{$placeholder}", replacement)
    }
    return result
}

fun Long.fileSizeToString(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val precision = when {
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    return "${SharedUiFormatter.formatDecimal(value, precision)} ${units[unitIndex]}"
}

fun Int.formatNumber(): String {
    val absValue = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""

    return when {
        absValue < 1_000 -> this.toString()
        absValue < 1_000_000 -> abbreviatedNumber(sign, absValue / 1_000.0, "K")
        absValue < 1_000_000_000 -> abbreviatedNumber(sign, absValue / 1_000_000.0, "M")
        else -> abbreviatedNumber(sign, absValue / 1_000_000_000.0, "B")
    }
}

private fun abbreviatedNumber(sign: String, value: Double, suffix: String): String {
    val formatted = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        value.toFixed(1)
    }
    return "$sign$formatted$suffix"
}

fun Float.toFixed(digits: Int = 0): String =
    SharedUiFormatter.formatDecimal(toDouble(), digits)

fun Double.toFixed(digits: Int = 0): String =
    SharedUiFormatter.formatDecimal(this, digits)
