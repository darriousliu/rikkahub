package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption

internal actual val platformLocalToolOptions: Set<LocalToolOption> = setOf(
    LocalToolOption.JavascriptEngine,
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.AskUser,
)

@Composable
internal actual fun rememberLocalToolPermissionGate(
    onScreenTimePermissionRequired: () -> Unit,
): (option: LocalToolOption) -> Boolean = remember { { true } }
