package me.rerere.rikkahub.ui.pages.setting

import coil3.PlatformContext
import me.rerere.rikkahub.utils.ShareUtil

internal actual fun onShareClick(
    shareText: String,
    context: PlatformContext,
    share: String,
    noShareApp: String
) {
    ShareUtil.shareText(shareText, share, noShareApp)
}
