package me.rerere.highlight

actual class Highlighter {
    actual suspend fun highlight(
        code: String,
        language: String
    ): ArrayList<HighlightToken> {
        TODO("Not yet implemented")
    }

    actual fun destroy() {
    }
}
