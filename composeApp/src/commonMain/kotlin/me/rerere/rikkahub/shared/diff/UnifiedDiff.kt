package me.rerere.rikkahub.shared.diff

import com.bernaferrari.difflib.DiffUtils
import com.bernaferrari.difflib.patch.AbstractDelta

private const val DEFAULT_CONTEXT_LINES = 3

/**
 * Generates a unified diff from [oldText] to [newText], or returns null when both texts are identical.
 */
fun generateUnifiedDiff(
    oldText: String,
    newText: String,
    path: String,
    contextLines: Int = DEFAULT_CONTEXT_LINES,
): String? {
    if (oldText == newText) return null

    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val deltas = DiffUtils.diff(oldLines, newLines).deltas
    if (deltas.isEmpty()) return null

    return buildList {
        add("--- a/$path")
        add("+++ b/$path")
        addAll(formatHunks(oldLines, deltas, contextLines))
    }.joinToString("\n")
}

private fun formatHunks(
    oldLines: List<String>,
    deltas: List<AbstractDelta<String>>,
    contextLines: Int,
): List<String> = buildList {
    val hunkDeltas = mutableListOf(deltas.first())
    var previousDelta = deltas.first()

    for (nextDelta in deltas.drop(1)) {
        val previousEndWithContext =
            previousDelta.source.position + previousDelta.source.size() + contextLines
        val nextStartWithContext = nextDelta.source.position - contextLines
        if (previousEndWithContext >= nextStartWithContext) {
            hunkDeltas += nextDelta
        } else {
            addAll(formatHunk(oldLines, hunkDeltas, contextLines))
            hunkDeltas.clear()
            hunkDeltas += nextDelta
        }
        previousDelta = nextDelta
    }

    addAll(formatHunk(oldLines, hunkDeltas, contextLines))
}

private fun formatHunk(
    oldLines: List<String>,
    deltas: List<AbstractDelta<String>>,
    contextLines: Int,
): List<String> {
    val body = mutableListOf<String>()
    var oldLineCount = 0
    var newLineCount = 0
    var currentDelta = deltas.first()

    val oldStart = (currentDelta.source.position + 1 - contextLines).coerceAtLeast(1)
    val newStart = (currentDelta.target.position + 1 - contextLines).coerceAtLeast(1)
    val leadingContextStart = (currentDelta.source.position - contextLines).coerceAtLeast(0)

    for (lineIndex in leadingContextStart until currentDelta.source.position) {
        body += " ${oldLines[lineIndex]}"
        oldLineCount++
        newLineCount++
    }

    body.addDelta(currentDelta)
    oldLineCount += currentDelta.source.size()
    newLineCount += currentDelta.target.size()

    for (nextDelta in deltas.drop(1)) {
        val intermediateStart = currentDelta.source.position + currentDelta.source.size()
        for (lineIndex in intermediateStart until nextDelta.source.position) {
            body += " ${oldLines[lineIndex]}"
            oldLineCount++
            newLineCount++
        }

        body.addDelta(nextDelta)
        oldLineCount += nextDelta.source.size()
        newLineCount += nextDelta.target.size()
        currentDelta = nextDelta
    }

    val trailingContextStart = currentDelta.source.position + currentDelta.source.size()
    val trailingContextEnd = minOf(trailingContextStart + contextLines, oldLines.size)
    for (lineIndex in trailingContextStart until trailingContextEnd) {
        body += " ${oldLines[lineIndex]}"
        oldLineCount++
        newLineCount++
    }

    body.add(0, "@@ -$oldStart,$oldLineCount +$newStart,$newLineCount @@")
    return body
}

private fun MutableList<String>.addDelta(delta: AbstractDelta<String>) {
    delta.source.lines.forEach { add("-$it") }
    delta.target.lines.forEach { add("+$it") }
}
