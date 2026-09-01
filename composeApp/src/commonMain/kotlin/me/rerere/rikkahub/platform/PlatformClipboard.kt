package me.rerere.rikkahub.platform

/** Plain-text clipboard access for code running outside composition, such as AI tools. */
internal expect object PlatformClipboard {
    fun readText(): String

    fun writeText(text: String)
}
