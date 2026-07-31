package me.rerere.rikkahub.ui.components.richtext

/** Describes how a formula should be laid out by the platform renderer. */
enum class LatexDisplayMode {
    Inline,
    Display,
}

/** A normalized formula with any complete outer delimiter pair removed. */
data class LatexFormula(
    val source: String,
    val displayMode: LatexDisplayMode,
)

/** Platform-independent dimensions used by inline text placeholders. */
data class LatexSize(
    val width: Float,
    val height: Float,
)

sealed interface LatexSplitResult {
    data class Segments(val values: List<String>) : LatexSplitResult

    data class Fallback(val source: String) : LatexSplitResult
}

/**
 * Removes a supported delimiter pair only when it encloses the complete trimmed input.
 * The delimiter determines display mode; otherwise [defaultDisplayMode] is retained.
 */
fun parseLatexFormula(
    latex: String,
    defaultDisplayMode: LatexDisplayMode = LatexDisplayMode.Inline,
): LatexFormula {
    val trimmed = latex.trim()
    return when {
        trimmed.length >= 4 && trimmed.startsWith("$$") && trimmed.endsWith("$$") -> {
            LatexFormula(
                source = trimmed.substring(2, trimmed.length - 2).trim(),
                displayMode = LatexDisplayMode.Display,
            )
        }

        trimmed.length >= 2 && trimmed.startsWith('$') && trimmed.endsWith('$') -> {
            LatexFormula(
                source = trimmed.substring(1, trimmed.length - 1).trim(),
                displayMode = LatexDisplayMode.Inline,
            )
        }

        trimmed.length >= 4 && trimmed.startsWith("\\[") && trimmed.endsWith("\\]") -> {
            LatexFormula(
                source = trimmed.substring(2, trimmed.length - 2).trim(),
                displayMode = LatexDisplayMode.Display,
            )
        }

        trimmed.length >= 4 && trimmed.startsWith("\\(") && trimmed.endsWith("\\)") -> {
            LatexFormula(
                source = trimmed.substring(2, trimmed.length - 2).trim(),
                displayMode = LatexDisplayMode.Inline,
            )
        }

        else -> LatexFormula(source = trimmed, displayMode = defaultDisplayMode)
    }
}

/**
 * Greedily groups a formula at top-level operators while keeping each splittable group within
 * [maxWidthPx]. An atom without a legal breakpoint remains intact even when it exceeds the limit.
 */
fun splitLatexFormula(
    source: String,
    maxWidthPx: Float,
    measureWidth: (String) -> Float,
): LatexSplitResult = runCatching {
    val splitPositions = findTopLevelSplitPositions(source)
    if (splitPositions.isEmpty()) {
        return@runCatching LatexSplitResult.Segments(listOf(source))
    }

    val candidateEnds = splitPositions + source.length
    val segments = mutableListOf<String>()
    var start = 0
    var lastFittingEnd = -1

    for (end in candidateEnds) {
        if (end <= start) continue

        val candidate = source.substring(start, end).trim()
        if (candidate.isEmpty()) continue

        if (measureWidth(candidate) <= maxWidthPx) {
            lastFittingEnd = end
            continue
        }

        if (lastFittingEnd > start) {
            segments += source.substring(start, lastFittingEnd).trim()
            start = lastFittingEnd
            lastFittingEnd = -1

            val remainder = source.substring(start, end).trim()
            if (remainder.isNotEmpty() && measureWidth(remainder) <= maxWidthPx) {
                lastFittingEnd = end
            }
        } else {
            segments += candidate
            start = end
            lastFittingEnd = -1
        }
    }

    if (start < source.length) {
        source.substring(start).trim().takeIf(String::isNotEmpty)?.let(segments::add)
    }

    LatexSplitResult.Segments(segments.ifEmpty { listOf(source) })
}.getOrElse {
    LatexSplitResult.Fallback(source)
}

private fun findTopLevelSplitPositions(source: String): List<Int> {
    val result = mutableListOf<Int>()
    var depth = 0
    var index = 0

    while (index < source.length) {
        when (val character = source[index]) {
            '{', '(', '[' -> depth++
            '}', ')', ']' -> if (depth > 0) depth--
            '\\' -> {
                val commandEnd = findCommandEnd(source, index)
                val command = source.substring(index, commandEnd)
                when (command) {
                    "\\left", "\\begin" -> depth++
                    "\\right", "\\end" -> if (depth > 0) depth--
                    in SPLIT_COMMANDS -> if (depth == 0) result += index
                }
                index = commandEnd
                continue
            }

            else -> if (depth == 0 && character in SPLIT_CHARACTERS) result += index
        }
        index++
    }

    return result
}

private fun findCommandEnd(source: String, slashIndex: Int): Int {
    var end = slashIndex + 1
    if (end >= source.length) return end
    if (!source[end].isLetter()) return end + 1

    while (end < source.length && source[end].isLetter()) end++
    return end
}

private val SPLIT_CHARACTERS = setOf('+', '-', '=', '<', '>')

private val SPLIT_COMMANDS = setOf(
    "\\leq", "\\le", "\\geq", "\\ge", "\\neq", "\\ne", "\\approx", "\\equiv", "\\sim", "\\simeq",
    "\\cong", "\\subset", "\\supset", "\\subseteq", "\\supseteq", "\\sqsubset", "\\sqsupset",
    "\\sqsubseteq", "\\sqsupseteq", "\\in", "\\notin", "\\ni", "\\to", "\\gets", "\\rightarrow",
    "\\leftarrow", "\\Rightarrow", "\\Leftarrow", "\\Leftrightarrow", "\\leftrightarrow", "\\longrightarrow",
    "\\longleftarrow", "\\Longrightarrow", "\\Longleftarrow", "\\Longleftrightarrow", "\\implies", "\\iff",
    "\\propto", "\\perp", "\\parallel", "\\vdash", "\\dashv", "\\models", "\\asymp", "\\bowtie",
    "\\smile", "\\frown", "\\pm", "\\mp", "\\times", "\\div", "\\cdot", "\\cup", "\\cap", "\\sqcup",
    "\\sqcap", "\\oplus", "\\ominus", "\\otimes", "\\oslash", "\\odot", "\\wedge", "\\vee", "\\setminus",
    "\\circ", "\\bullet", "\\star", "\\ast", "\\dagger", "\\ddagger", "\\amalg", "\\uplus",
)
