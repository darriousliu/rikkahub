package me.rerere.rikkahub.utils

import me.rerere.common.PlatformContext

expect object PlayStoreUtil {
    fun isInstalledFromPlayStore(context: PlatformContext): Boolean
}
