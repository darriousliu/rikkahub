package me.rerere.rikkahub.service

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath

internal fun PlatformFile.toFileUri(): String = "file://${absolutePath().toLocalFilePath().encodeURLPath()}"

internal fun String.toLocalFilePath(): String =
    if (startsWith("file:")) removePrefix("file:").removePrefix("//").decodeURLPart() else this
