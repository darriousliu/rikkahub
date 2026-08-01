package me.rerere.rikkahub.utils

fun String.extractQuotedContent(): List<String> {
    val result = mutableListOf<String>()
    val patterns = listOf(
        "\u201C([^\u201D]*?)\u201D",
        "\u2018([^\u2019]*?)\u2019",
        """"([^\"]*?)"""",
        """'([^']*?)'""",
        """「([^」]*?)」""",
        """『([^』]*?)』""",
    )
    patterns.forEach { pattern ->
        Regex(pattern).findAll(this).forEach { matchResult ->
            matchResult.groupValues[1].takeIf(String::isNotBlank)?.let(result::add)
        }
    }
    return result
}

fun String.extractQuotedContentAsText(separator: String = "\n"): String? =
    extractQuotedContent().takeIf(List<String>::isNotEmpty)?.joinToString(separator)

fun String.removeBracketedContent(): String? =
    """\([^)]*?\)|（[^）]*?）""".toRegex().replace(this, "").trim().ifBlank { null }
