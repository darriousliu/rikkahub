package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.ChatLiveUpdateNotification
import me.rerere.rikkahub.platform.ChatNotificationPhase
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.notificationTimeMillis
import me.rerere.rikkahub.platform.observeChatNotificationForeground
import kotlin.uuid.Uuid

// Live Update 通知节流间隔：流式输出每个 chunk 都会触发一次更新。
private const val LIVE_UPDATE_NOTIFICATION_THROTTLE_MS = 1000L

class ChatNotificationManager(
    appScope: CoroutineScope,
    eventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val presenter: ChatNotificationPresenter,
) {
    private val isForeground = MutableStateFlow(false)
    private val liveUpdateLastSentAt = mutableMapOf<Uuid, Long>()
    private var stopObservingForeground: (() -> Unit)? = null
    private val foregroundJob: Job
    private var collectionJob: Job?

    init {
        // ProcessLifecycleOwner 要求在主线程注册观察者。
        foregroundJob = appScope.launch {
            stopObservingForeground = observeChatNotificationForeground { isForeground.value = it }
        }
        collectionJob = appScope.launch(Dispatchers.Default) {
            eventBus.generationEvents.collect { event ->
                when (event) {
                    is AppEvent.ChatGenerationUpdate -> handleGenerationUpdate(event)
                    is AppEvent.ChatGenerationEnded -> handleGenerationEnded(event)
                    else -> {}
                }
            }
        }
    }

    private fun handleGenerationUpdate(event: AppEvent.ChatGenerationUpdate) {
        if (isForeground.value) return
        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        if (!displaySetting.enableNotificationOnMessageGeneration) return
        if (!displaySetting.enableLiveUpdateNotification) return

        val now = notificationTimeMillis()
        val lastSentAt = liveUpdateLastSentAt[event.conversationId]
        if (lastSentAt != null && now - lastSentAt < LIVE_UPDATE_NOTIFICATION_THROTTLE_MS) return
        liveUpdateLastSentAt[event.conversationId] = now

        sendLiveUpdateNotification(event.conversationId, event.lastMessage, event.senderName)
    }

    private fun handleGenerationEnded(event: AppEvent.ChatGenerationEnded) {
        cancelLiveUpdateNotification(event.conversationId)

        val contentPreview = event.contentPreview ?: return
        if (isForeground.value) return
        if (!settingsStore.settingsFlow.value.displaySetting.enableNotificationOnMessageGeneration) return
        sendGenerationDoneNotification(event.conversationId, event.senderName, contentPreview)
    }

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String, contentPreview: String) {
        presenter.showGenerationCompleted(conversationId, senderName, contentPreview)
    }

    private fun sendLiveUpdateNotification(conversationId: Uuid, lastMessage: UIMessage, senderName: String) {
        presenter.showLiveUpdate(ChatLiveUpdateNotification(
            conversationId = conversationId,
            senderName = senderName,
            phase = determineNotificationContent(lastMessage.parts),
        ))
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): ChatNotificationPhase {
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

    private fun cancelLiveUpdateNotification(conversationId: Uuid) {
        liveUpdateLastSentAt.remove(conversationId)
        presenter.cancelLiveUpdate(conversationId)
    }

    fun setForeground(isForeground: Boolean) {
        this.isForeground.value = isForeground
    }

    fun close() {
        foregroundJob.cancel()
        stopObservingForeground?.invoke()
        stopObservingForeground = null
        collectionJob?.let {
            it.cancel()
            presenter.close()
        }
        collectionJob = null
    }
}
