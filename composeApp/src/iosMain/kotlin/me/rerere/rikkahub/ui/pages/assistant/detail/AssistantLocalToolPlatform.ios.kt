package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.local.calendarAccess
import me.rerere.rikkahub.data.ai.tools.local.screenTimeAccess

@Composable
internal actual fun rememberLocalToolPermissionGate(
    onScreenTimePermissionRequired: () -> Unit,
): suspend (option: LocalToolOption) -> Boolean = remember {
    { option ->
        when (option) {
            LocalToolOption.Calendar -> calendarAccess.requestPermission()
            LocalToolOption.ScreenTime -> screenTimeAccess.requestPermission()
            else -> true
        }
    }
}
