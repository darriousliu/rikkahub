package me.rerere.common.android

import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext
import me.rerere.common.utils.toPlatformFile
import java.io.File

actual val PlatformContext.appTempFolder: PlatformFile
    get() {
        val dir = File(cacheDir, "temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.toPlatformFile()
    }

actual fun PlatformContext.getCacheDirectory(namespace: String): PlatformFile {
    val dir = File(cacheDir, "disk_cache/$namespace")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir.toPlatformFile()
}
