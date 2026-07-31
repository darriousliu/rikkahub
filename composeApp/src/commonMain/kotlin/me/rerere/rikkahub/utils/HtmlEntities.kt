package me.rerere.rikkahub.utils

import com.mohamedrejeb.ksoup.entities.KsoupEntities

private const val HTML5_APOSTROPHE_ENTITY = "&apos;"

fun String.escapeHtml(): String =
    KsoupEntities.encodeHtml4(this).replace(HTML5_APOSTROPHE_ENTITY, "'")

fun String.unescapeHtml(): String = buildString(length) {
    var index = 0
    while (index < this@unescapeHtml.length) {
        if (this@unescapeHtml[index] != '&') {
            append(this@unescapeHtml[index])
            index++
            continue
        }

        val entityEnd = this@unescapeHtml.findEntityEnd(index)
        if (entityEnd == null) {
            append('&')
            index++
            continue
        }

        val entity = this@unescapeHtml.substring(index, entityEnd + 1)
        val decoded = KsoupEntities.decodeHtml4(entity)
        val isNumeric = entity.startsWith("&#")
        val isUnsupportedNamedEntity =
            entity == HTML5_APOSTROPHE_ENTITY || (!isNumeric && decoded.endsWith(';'))
        append(if (decoded == entity || isUnsupportedNamedEntity) entity else decoded)
        index = entityEnd + 1
    }
}

private fun String.findEntityEnd(ampersandIndex: Int): Int? {
    var index = ampersandIndex + 1
    if (index >= length) return null

    if (this[index] == '#') {
        index++
        val isHexadecimal = index < length && (this[index] == 'x' || this[index] == 'X')
        if (isHexadecimal) index++
        val digitsStart = index
        while (
            index < length &&
            if (isHexadecimal) this[index].isAsciiHexDigit() else this[index] in '0'..'9'
        ) {
            index++
        }
        return if (index > digitsStart && index < length && this[index] == ';') index else null
    }

    if (!this[index].isAsciiLetter()) return null
    while (index < length && this[index].isAsciiLetterOrDigit()) index++
    return if (index < length && this[index] == ';') index else null
}

private fun Char.isAsciiHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'
