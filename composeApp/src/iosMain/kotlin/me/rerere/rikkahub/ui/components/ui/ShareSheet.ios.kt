package me.rerere.rikkahub.ui.components.ui

import me.rerere.common.PlatformContext
import me.rerere.rikkahub.utils.ShareUtil

actual fun shareModel(
    context: PlatformContext,
    state: ShareSheetState
) {
    ShareUtil.shareText(
        shareText = state.currentProvider?.encodeForShare() ?: ""
    )
}
