package me.rerere.rikkahub.utils

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.core.toByteArray
import net.sergeych.sprintf.format
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
    return encodeURLParameter()
}

fun String.urlDecode(): String {
    return decodeURLQueryComponent(plusIsSpace = true)
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String {
    return Base64.encode(this.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String {
    return Base64.decode(this).decodeToString()
}

expect fun String.escapeHtml(): String

expect fun String.unescapeHtml(): String

fun Number.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

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
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
        else -> "${this / (1024 * 1024 * 1024)} GB"
    }
}

fun Int.formatNumber(): String {
    val absValue = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""

    return when {
        absValue < 1000 -> this.toString()
        absValue < 1000000 -> {
            val value = absValue / 1000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}K"
            } else {
                "$sign${value.toFixed(1)}K"
            }
        }

        absValue < 1000000000 -> {
            val value = absValue / 1000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}M"
            } else {
                "$sign${value.toFixed(1)}M"
            }
        }

        else -> {
            val value = absValue / 1000000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}B"
            } else {
                "$sign${value.toFixed(1)}B"
            }
        }
    }
}

fun Float.toFixed(digits: Int = 0) = "%.${digits}f".format(this)
fun Double.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

/**
 * 提取字符串中所有引号内的内容
 * 支持多种引号类型：英文双引号 "..."、英文单引号 '...'、中文双引号 "..."、中文单引号 '...'
 * @return 所有引号内内容的列表
 */
fun String.extractQuotedContent(): List<String> {
    val result = mutableListOf<String>()
    // 匹配多种引号类型
    val patterns = listOf(
        """"([^"]*?)"""",  // 中文双引号
        """'([^']*?)'""",  // 中文单引号
        """"([^"]*?)"""",  // 英文双引号
        """'([^']*?)'""",  // 英文单引号
    )
    for (pattern in patterns) {
        val regex = Regex(pattern)
        regex.findAll(this).forEach { matchResult ->
            val content = matchResult.groupValues[1]
            if (content.isNotBlank()) {
                result.add(content)
            }
        }
    }
    return result
}

/**
 * 提取字符串中所有引号内的内容并合并为一个字符串
 * @param separator 分隔符，默认为换行
 * @return 合并后的字符串，如果没有引号内容则返回 null
 */
fun String.extractQuotedContentAsText(separator: String = "\n"): String? {
    val contents = extractQuotedContent()
    return if (contents.isNotEmpty()) {
        contents.joinToString(separator)
    } else {
        null
    }
}

fun String.toColorInt(): Int {
    return try {
        if (this.startsWith("#")) {
            this.substring(1).toLong(16).toInt()
        } else {
            this.toLong(16).toInt()
        }
    } catch (e: Exception) {
        0xFFFFFFFF.toInt()
    }
}
