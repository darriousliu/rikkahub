package me.rerere.rikkahub.service

import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.platform.PlatformDeviceInfo
import me.rerere.rikkahub.utils.applyPlaceholders
import kotlin.uuid.Uuid

/** Generates follow-up suggestions for a conversation. */
class ConversationSuggestionGenerator(
    private val providerManager: ProviderManager,
    private val getSettings: suspend () -> Settings,
    private val getConversation: suspend (Uuid) -> Conversation?,
    private val getLoadedConversation: (Uuid) -> Conversation?,
    private val updateConversation: (Uuid, Conversation) -> Unit,
    private val saveConversation: suspend (Uuid, Conversation) -> Unit,
    private val getLocaleName: () -> String = { PlatformDeviceInfo.localeName },
) {
    suspend fun generate(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = getSettings()
            if (!settings.enableSuggestion) return
            val model = settings.findModelById(settings.suggestionModelId, fallback = settings.fastModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            getLoadedConversation(conversationId)?.let {
                updateConversation(conversationId, it.copy(chatSuggestions = emptyList()))
            }

            val result = providerManager.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to getLocaleName(),
                            "content" to conversation.currentMessages.takeLast(8)
                                .joinToString("\n\n") { it.summaryAsText(maxLength = 500) },
                        ),
                    ),
                ),
                params = backgroundTextGenerationParams(model),
            )
            val suggestions = result.choices[0].message?.toText()
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val latestConversation = getConversation(conversationId)
                ?: getLoadedConversation(conversationId)
                ?: conversation
            saveConversation(conversationId, latestConversation.copy(chatSuggestions = suggestions.take(10)))
        }.onFailure {
            it.printStackTrace()
        }
    }
}
