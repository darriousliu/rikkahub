package me.rerere.rikkahub.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import me.rerere.rikkahub.service.toLocalFilePath
import java.io.File
import java.net.JarURLConnection
import java.net.URI

fun initializeDesktopImageLoader() {
    SingletonImageLoader.setSafe { context -> createDesktopImageLoader(context) }
}

internal fun createDesktopImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components { add(DesktopLocalImageMapper()) }
    .build()

private class DesktopLocalImageMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? = when {
        data.startsWith("jar:file:", ignoreCase = true) -> {
            val connection = URI(data).toURL().openConnection() as JarURLConnection
            val jarPath = File(connection.jarFileURL.toURI()).path
            // Coil's parser treats the drive letter in jar:file:/C:/... as part of the scheme.
            Uri(scheme = "jar:file", path = "$jarPath!/${connection.entryName}")
        }

        data.startsWith("file:", ignoreCase = true) -> {
            val path = runCatching { File(URI(data)).path }.getOrElse {
                // Existing attachments use file://C:%5C... on Windows.
                data.toLocalFilePath()
            }
            Uri(scheme = "file", path = path)
        }

        data.startsWith('/') || data.startsWith("\\\\") || windowsPath.containsMatchIn(data) -> {
            // Raw paths can contain literal # and %; do not parse them as URI fragments/escapes.
            Uri(scheme = "file", path = File(data).path)
        }

        else -> null
    }

    private companion object {
        val windowsPath = Regex("^[A-Za-z]:[/\\\\]")
    }
}
