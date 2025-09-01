package me.rerere.common.cache

import io.github.vinceglb.filekit.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import me.rerere.common.utils.delete
import me.rerere.common.utils.mkdirs
import me.rerere.common.utils.renameTo

internal fun ensureParentDir(file: PlatformFile) {
    val parent = file.parent()
    if (parent != null && !parent.exists()) {
        if (!parent.mkdirs() && !parent.exists()) {
            throw IOException("Failed to create directory: $parent")
        }
    }
}

internal fun atomicWrite(file: PlatformFile, content: String) = runBlocking {
    ensureParentDir(file)
    val tmp = PlatformFile(file.parent()!!, file.name + ".tmp")
    tmp.writeString(content)
    if (file.exists()) {
        if (!tmp.renameTo(file)) {
            if (file.delete()) {
                if (!tmp.renameTo(file)) {
                    throw IOException("Failed to replace $file with temp file")
                }
            } else {
                throw IOException("Failed to delete $file for atomic write")
            }
        }
    } else {
        if (!tmp.renameTo(file)) {
            throw IOException("Failed to move temp file to $file")
        }
    }
}

