package me.rerere.rikkahub.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.platform.PlatformDeviceInfo
import me.rerere.rikkahub.utils.applyPlaceholders
import kotlin.uuid.Uuid

/**
 * Generates follow-up suggestions without replacing the conversation snapshot.
 * [saveSuggestions] must update only suggestions, while stored messages still match the expected messages.
 */
class ConversationSuggestionGenerator(
    private val providerManager: ProviderManager,
    private val getSettings: suspend () -> Settings,
    private val getConversation: suspend (Uuid) -> Conversation?,
    private val clearSuggestions: suspend (id: Uuid, expectedMessages: List<UIMessage>) -> Unit,
    private val saveSuggestions: suspend (
        id: Uuid,
        expectedMessages: List<UIMessage>,
        suggestions: List<String>,
    ) -> Unit,
    private val getLocaleName: () -> String = { PlatformDeviceInfo.localeName },
) {
    private val requests = MutableStateFlow<Map<Uuid, Uuid>>(emptyMap())
    private val updates = Mutex()

    suspend fun generate(conversationId: Uuid, conversation: Conversation) {
        val settings = getSettings()
        if (!settings.enableSuggestion) return
        val model = settings.findModelById(settings.suggestionModelId, fallback = settings.fastModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val expectedMessages = conversation.currentMessages
        if (getConversation(conversationId)?.currentMessages != expectedMessages) return

        val requestId = Uuid.random()
        requests.update { it + (conversationId to requestId) }
        try {
            updates.withLock {
                if (requests.value[conversationId] != requestId) return
                clearSuggestions(conversationId, expectedMessages)
            }
            val result = providerManager.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to getLocaleName(),
                            "content" to expectedMessages.takeLast(8)
                                .joinToString("\n\n") { it.summaryAsText(maxLength = 500) },
                        ),
                    ),
                ),
                params = backgroundTextGenerationParams(model),
            )
            currentCoroutineContext().ensureActive()
            val suggestions = result.choices.firstOrNull()?.message?.toText()
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.take(10)
                .orEmpty()
            updates.withLock {
                if (requests.value[conversationId] != requestId) return
                if (!getSettings().enableSuggestion) return
                if (getConversation(conversationId)?.currentMessages != expectedMessages) return
                saveSuggestions(conversationId, expectedMessages, suggestions)
            }
        } finally {
            requests.update { current ->
                if (current[conversationId] == requestId) current - conversationId else current
            }
        }
    }
}
