package me.rerere.rikkahub.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.shareFile

actual suspend fun sharePlatformFile(file: PlatformFile) {
    FileKit.shareFile(file)
}
