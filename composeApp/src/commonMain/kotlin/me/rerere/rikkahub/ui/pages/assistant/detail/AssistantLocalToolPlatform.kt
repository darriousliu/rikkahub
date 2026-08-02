package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption

internal expect val platformLocalToolOptions: Set<LocalToolOption>

/**
 * Returns whether [option] may be enabled immediately. Platform permission UIs are presented here when needed.
 */
@Composable
internal expect fun rememberLocalToolPermissionGate(
    onScreenTimePermissionRequired: () -> Unit,
): (option: LocalToolOption) -> Boolean
