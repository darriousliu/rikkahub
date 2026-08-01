package me.rerere.rikkahub.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import kotlin.uuid.Uuid

private const val LIVE_UPDATE_THROTTLE_MILLIS: Long = 1_000L

public sealed interface ChatNotificationPhase {
    public data class Tool(
        val toolName: String,
        val inputPreview: String,
    ) : ChatNotificationPhase

    public data class Thinking(val preview: String) : ChatNotificationPhase

    public data class Writing(val preview: String) : ChatNotificationPhase

    public data object Starting : ChatNotificationPhase
}

public data class ChatLiveUpdateNotification(
    val conversationId: Uuid,
    val senderName: String,
    val phase: ChatNotificationPhase,
)

public interface ChatNotificationPresenter {
    public fun showLiveUpdate(notification: ChatLiveUpdateNotification)

    public fun showGenerationCompleted(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String,
    )

    public fun cancelLiveUpdate(conversationId: Uuid)

    public fun close() {}
}

public expect class ChatNotificationManager() {
    public fun start(
        scope: CoroutineScope,
        eventBus: AppEventBus,
        settingsStore: SettingsStore,
        presenter: ChatNotificationPresenter,
    )

    public fun setForeground(isForeground: Boolean)

    public fun close()
}

internal class ChatNotificationPolicy(
    private val throttleMillis: Long = LIVE_UPDATE_THROTTLE_MILLIS,
) {
    private val liveUpdateLastSentAt = mutableMapOf<Uuid, Long>()

    fun shouldShowLiveUpdate(
        conversationId: Uuid,
        nowMillis: Long,
        isForeground: Boolean,
        notificationsEnabled: Boolean,
        liveUpdatesEnabled: Boolean,
    ): Boolean {
        if (isForeground || !notificationsEnabled || !liveUpdatesEnabled) return false
        val lastSentAt = liveUpdateLastSentAt[conversationId]
        if (lastSentAt != null && nowMillis - lastSentAt < throttleMillis) return false
        liveUpdateLastSentAt[conversationId] = nowMillis
        return true
    }

    fun generationEnded(conversationId: Uuid) {
        liveUpdateLastSentAt.remove(conversationId)
    }

    fun shouldShowCompletion(
        isForeground: Boolean,
        notificationsEnabled: Boolean,
        contentPreview: String?,
    ): Boolean = !isForeground && notificationsEnabled && contentPreview != null
}

internal fun resolveChatNotificationPhase(parts: List<UIMessagePart>): ChatNotificationPhase {
    val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
    val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
    val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

    return when {
        lastTool != null && !lastTool.isExecuted -> ChatNotificationPhase.Tool(
            toolName = lastTool.toolName.substringAfterLast("__"),
            inputPreview = lastTool.input.take(100),
        )

        lastReasoning != null && lastReasoning.finishedAt == null ->
            ChatNotificationPhase.Thinking(lastReasoning.reasoning.takeLast(200))

        lastText != null -> ChatNotificationPhase.Writing(lastText.text.takeLast(200))
        else -> ChatNotificationPhase.Starting
    }
}

internal class ChatNotificationCoordinator(
    scope: CoroutineScope,
    eventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val foreground: StateFlow<Boolean>,
    private val presenter: ChatNotificationPresenter,
    private val monotonicMillis: () -> Long,
) {
    private val policy = ChatNotificationPolicy()
    private val collectionJob: Job = scope.launch(Dispatchers.Default) {
        eventBus.events.collect { event ->
            when (event) {
                is AppEvent.ChatGenerationUpdate -> handleGenerationUpdate(event)
                is AppEvent.ChatGenerationEnded -> handleGenerationEnded(event)
                else -> Unit
            }
        }
    }

    private fun handleGenerationUpdate(event: AppEvent.ChatGenerationUpdate) {
        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        if (
            !policy.shouldShowLiveUpdate(
                conversationId = event.conversationId,
                nowMillis = monotonicMillis(),
                isForeground = foreground.value,
                notificationsEnabled = displaySetting.enableNotificationOnMessageGeneration,
                liveUpdatesEnabled = displaySetting.enableLiveUpdateNotification,
            )
        ) {
            return
        }
        presenter.showLiveUpdate(
            ChatLiveUpdateNotification(
                conversationId = event.conversationId,
                senderName = event.senderName,
                phase = resolveChatNotificationPhase(event.lastMessage.parts),
            ),
        )
    }

    private fun handleGenerationEnded(event: AppEvent.ChatGenerationEnded) {
        policy.generationEnded(event.conversationId)
        presenter.cancelLiveUpdate(event.conversationId)

        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        if (
            policy.shouldShowCompletion(
                isForeground = foreground.value,
                notificationsEnabled = displaySetting.enableNotificationOnMessageGeneration,
                contentPreview = event.contentPreview,
            )
        ) {
            presenter.showGenerationCompleted(
                conversationId = event.conversationId,
                senderName = event.senderName,
                contentPreview = checkNotNull(event.contentPreview),
            )
        }
    }

    fun close() {
        collectionJob.cancel()
        presenter.close()
    }
}
