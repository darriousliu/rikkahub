package me.rerere.rikkahub.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSURL
import platform.posix.rename

actual val Path.canonicalFile: Path
    get() {
        val absolute = Path(NSURL.fileURLWithPath(toString()).URLByStandardizingPath!!.path!!)
        // NSURL 在最终文件不存在时可能不解析父目录的链接；File.canonicalFile 仍会解析已有的前缀。
        val parent = absolute.parent
        return if (!absolute.exists() && parent != null) {
            parent.canonicalFile.resolve(absolute.name)
        } else {
            Path(NSURL.fileURLWithPath(absolute.toString()).URLByResolvingSymlinksInPath!!.path!!)
        }
    }

@OptIn(ExperimentalForeignApi::class)
actual fun Path.renameTo(destination: Path): Boolean = rename(toString(), destination.toString()) == 0
