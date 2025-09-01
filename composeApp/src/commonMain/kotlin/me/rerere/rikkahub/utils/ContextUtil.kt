package me.rerere.rikkahub.utils

import coil3.BitmapImage
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext
import kotlin.time.Clock

expect fun PlatformContext.readClipboardText(): String

expect fun PlatformContext.joinQQGroup(key: String?): Boolean

expect fun PlatformContext.writeClipboardText(text: String)

expect fun PlatformContext.openUrl(url: String)

expect fun PlatformContext.exportImage(
    activity: PlatformContext,
    bitmap: BitmapImage,
    fileName: String = "RikkaHub_${Clock.System.now().toEpochMilliseconds()}.png"
)

expect fun PlatformContext.exportImageFile(
    activity: PlatformContext,
    file: PlatformFile,
    fileName: String = "RikkaHub_${Clock.System.now().toEpochMilliseconds()}.png"
)

