@file:Suppress("NOTHING_TO_INLINE")

package me.rerere.common.utils

import androidx.core.net.toFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.File

inline fun File.toPlatformFile() = PlatformFile(this)

inline fun PlatformFile.toFile() = androidFile.let {
    when (it) {
        is AndroidFile.FileWrapper -> it.file
        is AndroidFile.UriWrapper -> it.uri.toFile()
    }
}
