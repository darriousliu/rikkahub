package me.rerere.rikkahub.utils

import coil3.PlatformContext

actual fun UpdateChecker.downloadUpdate(context: PlatformContext, download: UpdateDownload) {
    error("APK update download is only supported on Android")
}
