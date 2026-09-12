package me.rerere.rikkahub.utils

import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

// java.io.File 的同步 I/O 和 Boolean 结果约定；供 Manager、文件树及平台备份接线共用。
fun Path.resolve(child: String): Path = Path(child).let { if (it.isAbsolute) it else Path(this, child) }

expect val Path.canonicalFile: Path

expect fun Path.canRead(): Boolean

expect fun Path.isSymbolicLink(): Boolean

fun Path.exists(): Boolean = SystemFileSystem.exists(this)

val Path.isDirectory: Boolean get() = SystemFileSystem.metadataOrNull(this)?.isDirectory == true

val Path.isFile: Boolean get() = SystemFileSystem.metadataOrNull(this)?.isRegularFile == true

fun Path.length(): Long = SystemFileSystem.metadataOrNull(this)?.size ?: 0L

fun Path.listFiles(): List<Path>? = try {
    SystemFileSystem.list(this).toList()
} catch (_: IOException) {
    null
}

fun Path.mkdirs(): Boolean {
    if (exists()) return false
    return try {
        SystemFileSystem.createDirectories(this)
        true
    } catch (_: IOException) {
        false
    }
}

fun Path.readText(): String = SystemFileSystem.source(this).buffered().use { it.readString() }

fun Path.writeBytes(bytes: ByteArray) = SystemFileSystem.sink(this).buffered().use { it.write(bytes) }

fun Path.writeText(content: String) = writeBytes(content.encodeToByteArray())

expect fun Path.renameTo(destination: Path): Boolean

expect fun Path.lastModified(): Long

expect fun Path.setLastModified(timeMillis: Long): Boolean

fun Path.delete(): Boolean = try {
    SystemFileSystem.delete(this)
    true
} catch (_: IOException) {
    false
}

fun Path.deleteRecursively(): Boolean {
    val childrenDeleted = if (isDirectory) {
        listFiles().orEmpty().fold(true) { result, child -> child.deleteRecursively() && result }
    } else {
        true
    }
    return (delete() || !exists()) && childrenDeleted
}
