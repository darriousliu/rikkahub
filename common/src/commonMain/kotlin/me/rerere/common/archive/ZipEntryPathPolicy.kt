package me.rerere.common.archive

object ZipEntryPathPolicy {
    fun normalizeOrNull(rawPath: String): String? {
        val parts = rawPath.replace('\\', '/')
            .trimStart('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }
}
