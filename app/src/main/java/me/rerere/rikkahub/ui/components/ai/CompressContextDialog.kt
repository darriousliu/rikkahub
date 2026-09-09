package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator

/** Android facade preserves the existing call and loading indicator. */
@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
) {
    SharedCompressContextDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        loadingIndicator = { RabbitLoadingIndicator(modifier = it) },
    )
}
