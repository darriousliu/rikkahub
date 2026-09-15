package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isLinux
import me.rerere.rikkahub.shared.isWindows

internal val platformLocalToolOptions: Set<LocalToolOption> = buildSet {
    add(LocalToolOption.JavascriptEngine)
    add(LocalToolOption.TimeInfo)
    add(LocalToolOption.Clipboard)
    add(LocalToolOption.Tts)
    add(LocalToolOption.AskUser)
    if (currentPlatformKind != PlatformKind.DESKTOP) add(LocalToolOption.ScreenTime)
    if (!currentPlatformKind.isLinux && !currentPlatformKind.isWindows) add(LocalToolOption.Calendar)
}

/**
 * Returns whether [option] may be enabled immediately. Platform permission UIs are presented here when needed.
 */
@Composable
internal expect fun rememberLocalToolPermissionGate(
    onScreenTimePermissionRequired: () -> Unit,
): suspend (option: LocalToolOption) -> Boolean
