package me.rerere.rikkahub.utils

import kotlinx.io.files.Path
import java.io.File

actual val Path.canonicalFile: Path get() = Path(File(toString()).canonicalPath)

actual fun Path.canRead(): Boolean = File(toString()).canRead()

actual fun Path.isSymbolicLink(): Boolean = java.nio.file.Files.isSymbolicLink(File(toString()).toPath())

actual fun Path.renameTo(destination: Path): Boolean = File(toString()).renameTo(File(destination.toString()))
