package me.rerere.rikkahub.service

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import kotlinx.io.files.SystemPathSeparator

internal fun PlatformFile.toFileUri(): String = "file://${absolutePath().toLocalFilePath().encodeURLPath()}"

internal fun String.toLocalFilePath(): String {
    if (!startsWith("file:")) return this
    val path = removePrefix("file:").removePrefix("//").decodeURLPart()
    // JVM file URIs use /C:/..., while Windows file APIs expect C:/....
    return if (SystemPathSeparator == '\\' && windowsDriveUriPath.containsMatchIn(path)) {
        path.drop(1)
    } else {
        path
    }
}

private val windowsDriveUriPath = Regex("^/[A-Za-z]:[/\\\\]")
