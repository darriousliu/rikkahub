package me.rerere.common.archive

object ZipEntryPathPolicy {
    fun normalizeOrNull(rawPath: String): String? {
        if ('\u0000' in rawPath) return null

        val normalizedSeparators = rawPath.replace('\\', '/')
        if (normalizedSeparators.startsWith('/')) return null

        val parts = normalizedSeparators
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." || it.hasWindowsDriveQualifier() }) return null
        return parts.joinToString("/")
    }

    fun relativeToRootOrNull(rawPath: String, root: String): String? {
        val normalizedPath = normalizeOrNull(rawPath) ?: return null
        val normalizedRoot = normalizeOrNull(root) ?: return null
        if (normalizedPath == normalizedRoot) return ""

        val prefix = "$normalizedRoot/"
        return normalizedPath.removePrefix(prefix).takeIf { it != normalizedPath }
    }

    fun directChildOfOrNull(rawPath: String, root: String): String? {
        val relativePath = relativeToRootOrNull(rawPath, root) ?: return null
        return relativePath.takeIf { it.isNotEmpty() && '/' !in it }
    }

    private fun String.hasWindowsDriveQualifier(): Boolean =
        length >= 2 && this[0].isLetter() && this[1] == ':'
}
