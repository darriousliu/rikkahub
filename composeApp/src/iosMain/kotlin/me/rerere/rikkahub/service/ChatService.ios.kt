package me.rerere.rikkahub.service

import androidx.lifecycle.LifecycleEventObserver
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

// TODO("Not yet implemented")
actual fun init(lifecycleObserver: LifecycleEventObserver) {

}

// TODO("Not yet implemented")
actual fun cleanup(lifecycleObserver: LifecycleEventObserver, block: () -> Unit) {

}

// TODO("Not yet implemented")
internal actual fun sendGenerationDoneNotification(
    conversation: Conversation,
    context: PlatformContext,
    conversationId: Uuid
) {
}
