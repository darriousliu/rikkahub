
package me.rerere.common.utils

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import kotlinx.coroutines.runBlocking

fun PlatformFile.mkdirs() = try {
    createDirectories()
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun PlatformFile.delete() = runBlocking {
    try {
        delete()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun PlatformFile.renameTo(file: PlatformFile) = runBlocking {
    try {
        atomicMove(file)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
