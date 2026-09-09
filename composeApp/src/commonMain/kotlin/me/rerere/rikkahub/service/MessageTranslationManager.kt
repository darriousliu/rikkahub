package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

class MessageTranslationManager(
    private val scope: CoroutineScope,
    private val getSettings: suspend () -> Settings,
    private val translateText: (Settings, String, String, String, ((String) -> Unit)?) -> Flow<String>,
    private val getConversation: (Uuid) -> Conversation,
    private val updateConversation: (Uuid, Conversation) -> Unit,
    private val saveConversation: suspend (Uuid, Conversation) -> Unit,
    private val getLoadingText: suspend () -> String,
    private val onError: suspend (Uuid, Throwable) -> Unit,
) {
    fun translate(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguageCode: String,
        targetLanguageName: String,
    ): Job = scope.launch {
        try {
            val settings = getSettings()
            val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n\n") { it.text }
                .trim()

            if (messageText.isBlank()) return@launch

            val loading = getLoadingText()
            updateTranslationField(conversationId, message.id, loading)

            translateText(settings, messageText, targetLanguageCode, targetLanguageName) { translatedText ->
                updateTranslationField(conversationId, message.id, translatedText)
            }.collect { }

            saveConversation(conversationId, getConversation(conversationId))
        } catch (error: Exception) {
            clearTranslationField(conversationId, message.id)
            onError(conversationId, error)
        }
    }

    fun clear(conversationId: Uuid, messageId: Uuid) {
        clearTranslationField(conversationId, messageId)
    }

    private fun updateTranslationField(conversationId: Uuid, messageId: Uuid, translationText: String) {
        val currentConversation = getConversation(conversationId)
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(translation = translationText)
                    } else {
                        message
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }
        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    private fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversation(conversationId)
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(translation = null)
                    } else {
                        message
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }
        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }
}
