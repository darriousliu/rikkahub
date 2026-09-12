package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job

/** Android facade preserves the existing call. */
@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
) {
    SharedCompressContextDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
