package me.rerere.common.android

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import me.rerere.common.PlatformContext
import platform.Foundation.*

actual val PlatformContext.appTempFolder: PlatformFile
    get() {
        val tempDir = NSTemporaryDirectory()
        val appTempPath = "${tempDir}temp"
        val file = PlatformFile(NSURL.fileURLWithPath(appTempPath))

        // 创建目录（如果不存在）
        if (!file.exists()) {
            file.createDirectories()
        }

        return file
    }

actual fun PlatformContext.getCacheDirectory(namespace: String): PlatformFile {
// 获取 Caches 目录
    val cachesDir = getCachesDirectory()
    val namespacePath = "$cachesDir/disk_cache/$namespace"
    val file = PlatformFile(NSURL.fileURLWithPath(namespacePath))

    // 创建目录（如果不存在）
    if (!file.exists()) {
        file.createDirectories()
    }

    return file
}

// 辅助函数：获取 iOS Caches 目录
private fun getCachesDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    )
    return paths.firstOrNull() as? String ?: ""
}
