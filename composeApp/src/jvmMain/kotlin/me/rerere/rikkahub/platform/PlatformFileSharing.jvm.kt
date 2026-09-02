package me.rerere.rikkahub.platform

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * 桌面端没有系统分享面板，改为用默认应用打开文件，让用户自行处置。
 */
actual suspend fun sharePlatformFile(file: PlatformFile) {
    withContext(Dispatchers.IO) {
        val target = File(file.absolutePath())
        if (!target.exists()) return@withContext
        if (!Desktop.isDesktopSupported()) return@withContext
        val desktop = Desktop.getDesktop()
        when {
            desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(target)
            desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR) -> desktop.browseFileDirectory(target)
            else -> Unit
        }
    }
}
