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

/**
 * Shared title generation; runtimes retain persistence and error presentation.
 * [saveTitle] must update only the title, and only while the stored title still matches its expected value.
 */
class ConversationTitleGenerator(
    private val providerManager: ProviderManager,
    private val getSettings: suspend () -> Settings,
    private val getConversation: suspend (Uuid) -> Conversation?,
    private val saveTitle: suspend (id: Uuid, expectedTitle: String, title: String) -> Unit,
    private val getLocaleName: () -> String = { PlatformDeviceInfo.localeName },
) {
    suspend fun generate(conversationId: Uuid, conversation: Conversation, force: Boolean = false) {
        if (!force && conversation.title.isNotBlank()) return

        val settings = getSettings()
        val model = settings.findModelById(settings.titleModelId, fallback = settings.fastModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val result = providerManager.getProviderByType(provider).generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage.user(
                    settings.titlePrompt.applyPlaceholders(
                        "locale" to getLocaleName(),
                        "content" to conversation.currentMessages.takeLast(4)
                            .joinToString("\n\n") { it.summaryAsText(maxLength = 500) },
                    ),
                ),
            ),
            params = backgroundTextGenerationParams(model),
        )
        val title = result.choices.firstOrNull()?.message?.toText()?.trim()?.takeIf { it.isNotBlank() } ?: return

        // The conversation may have gained messages, been renamed, or been deleted during the request.
        val latest = getConversation(conversationId) ?: return
        if (latest.title != conversation.title) return
        saveTitle(conversationId, latest.title, title)
    }
}
