package me.rerere.rikkahub.utils

import me.rerere.common.PlatformContext

actual fun platformDownloadUpdate(
    context: PlatformContext,
    download: UpdateDownload
) {
    context.openUrl(download.url)
}
