package me.rerere.rikkahub.service

import androidx.lifecycle.LifecycleEventObserver
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

actual fun init(lifecycleObserver: LifecycleEventObserver) {

}

actual fun cleanup(lifecycleObserver: LifecycleEventObserver, block: () -> Unit) {

}

internal actual fun sendGenerationDoneNotification(
    conversation: Conversation,
    context: PlatformContext,
    conversationId: Uuid
) {
}
