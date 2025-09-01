package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import me.rerere.rikkahub.utils.PlayStoreUtil

@Composable
fun rememberIsPlayStoreVersion(): Boolean {
    val context = LocalPlatformContext.current
    return remember {
        PlayStoreUtil.isInstalledFromPlayStore(context)
    }
}
