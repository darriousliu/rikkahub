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
 */
class ConversationTitleGenerator(
    private val providerManager: ProviderManager,
    private val getSettings: suspend () -> Settings,
    private val getConversation: suspend (Uuid) -> Conversation?,
    private val saveConversation: suspend (Uuid, Conversation) -> Unit,
    private val onError: suspend (Uuid, Throwable) -> Unit,
    private val getLocaleName: () -> String = { PlatformDeviceInfo.localeName },
) {
    suspend fun generate(conversationId: Uuid, conversation: Conversation, force: Boolean = false) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        runCatching {
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
            getConversation(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: ""),
                )
            }
        }.onFailure {
            it.printStackTrace()
            onError(conversationId, it)
        }
    }
}
