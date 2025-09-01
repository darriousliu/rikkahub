
package me.rerere.common.utils

import coil3.Uri
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.runBlocking
import me.rerere.common.PlatformContext

fun PlatformFile.mkdirs() = try {
    createDirectories()
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun PlatformFile.delete() = runBlocking {
    try {
        delete(mustExist = false)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

expect fun PlatformFile.deleteRecursively(): Boolean

fun PlatformFile.renameTo(file: PlatformFile) = runBlocking {
    try {
        atomicMove(file)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun PlatformFile.createNewFile() = runBlocking {
    try {
        if (!exists()) {
            write(byteArrayOf())
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

expect fun PlatformFile.toUri(context: PlatformContext): Uri

expect fun getUriForFile(context: PlatformContext, file: PlatformFile): Uri
