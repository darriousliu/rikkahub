package me.rerere.common.android

import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext

expect val PlatformContext.appTempFolder: PlatformFile

expect fun PlatformContext.getCacheDirectory(namespace: String): PlatformFile
