package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

/** Coordinates message translations independently of the conversation's chat generation job. */
class MessageTranslationManager(
    private val scope: CoroutineScope,
    private val getSettings: suspend () -> Settings,
    private val translateText: (Settings, String, String, String) -> Flow<String>,
    private val getConversation: (Uuid) -> Conversation?,
    private val updateTranslation: (Uuid, UIMessage, String?) -> Unit,
    private val saveTranslation: suspend (Uuid, UIMessage, String?) -> Boolean,
    private val getLoadingText: suspend () -> String,
    private val onError: suspend (Uuid, Throwable) -> Unit,
) {
    private data class Key(val conversationId: Uuid, val messageId: Uuid)

    // This lock linearizes request ownership with every in-memory translation update.
    // Database writes use [writes] separately and never occur while this lock is held.
    private val state = Mutex()
    private val requests = mutableMapOf<Key, Uuid>()
    private val writes = Mutex()

    fun translate(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguageCode: String,
        targetLanguageName: String,
    ): Job? {
        val source = message.parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n\n") { it.text }
            .trim()
        if (source.isBlank()) return null
        val key = Key(conversationId, message.id)
        if (currentMessage(key)?.matchesTranslationSource(message) != true) return null

        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val request = reserve(key, message) ?: return@launch
            var lastPublished: String? = null
            try {
                yield()
                val settings = getSettings()
                val loading = getLoadingText()
                publish(key, request, message, loading) { lastPublished = loading }

                var result: String? = null
                translateText(settings, source, targetLanguageCode, targetLanguageName).collect { text ->
                    if (text.isNotBlank()) {
                        publish(key, request, message, text) {
                            result = text
                            lastPublished = text
                        }
                    } else {
                        requireCurrent(key, request, message)
                    }
                }
                requireCurrent(key, request, message)
                writes.withLock {
                    requireCurrent(key, request, message)
                    val saved = saveTranslation(conversationId, message, result)
                    publishIfCurrent(key, request, message, if (saved) result else null)
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    runCatching { clearOwnedTranslation(key, request, message, lastPublished) }
                        .exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            } catch (error: Exception) {
                if (isCurrent(key, request)) {
                    runCatching { clearOwnedTranslation(key, request, message, lastPublished) }
                        .exceptionOrNull()?.let(error::addSuppressed)
                    if (isCurrent(key, request)) onError(conversationId, error)
                }
            } finally {
                release(key, request)
            }
        }
    }

    /** Clear immediately in memory, invalidate in-flight results, then persist only this field. */
    fun clear(conversationId: Uuid, messageId: Uuid): Job? {
        val key = Key(conversationId, messageId)
        if (currentMessage(key) == null) return null
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val reservation = reserveForClear(key) ?: return@launch
            val (request, message) = reservation
            try {
                yield()
                writes.withLock {
                    if (isCurrent(key, request)) saveTranslation(conversationId, message, null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(key, request)) onError(conversationId, error)
            } finally {
                release(key, request)
            }
        }
    }

    private suspend fun clearOwnedTranslation(
        key: Key,
        request: Uuid,
        expectedMessage: UIMessage,
        lastPublished: String?,
    ) {
        writes.withLock {
            if (!isCurrent(key, request)) return
            val current = currentMessage(key) ?: return
            // An in-place edit can retain our loading/partial text. Clear only the value we still own.
            if (!current.matchesTranslationSource(expectedMessage) &&
                (lastPublished == null || current.translation != lastPublished)
            ) return
            publishIfCurrent(key, request, current, null)
            saveTranslation(key.conversationId, current, null)
        }
    }

    private suspend fun requireCurrent(key: Key, request: Uuid, message: UIMessage) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(key, request, message)) {
            throw CancellationException("Message translation is no longer current")
        }
    }

    private suspend fun reserve(key: Key, message: UIMessage): Uuid? = state.withLock {
        if (currentMessage(key)?.matchesTranslationSource(message) != true) return@withLock null
        Uuid.random().also { requests[key] = it }
    }

    private suspend fun reserveForClear(key: Key): Pair<Uuid, UIMessage>? = state.withLock {
        val message = currentMessage(key) ?: return@withLock null
        val request = Uuid.random()
        requests[key] = request
        updateTranslation(key.conversationId, message, null)
        request to message
    }

    private suspend fun publish(
        key: Key,
        request: Uuid,
        message: UIMessage,
        translation: String?,
        onPublished: () -> Unit,
    ) = state.withLock {
        currentCoroutineContext().ensureActive()
        if (!isCurrentLocked(key, request) || currentMessage(key)?.matchesTranslationSource(message) != true) {
            throw CancellationException("Message translation is no longer current")
        }
        updateTranslation(key.conversationId, message, translation)
        onPublished()
    }

    private suspend fun publishIfCurrent(
        key: Key,
        request: Uuid,
        message: UIMessage,
        translation: String?,
    ) = state.withLock {
        if (isCurrentLocked(key, request) && currentMessage(key)?.matchesTranslationSource(message) == true) {
            updateTranslation(key.conversationId, message, translation)
        }
    }

    private fun currentMessage(key: Key): UIMessage? = getConversation(key.conversationId)
        ?.getMessageNodeByMessageId(key.messageId)?.messages?.firstOrNull { it.id == key.messageId }

    private suspend fun isCurrent(key: Key, request: Uuid): Boolean = state.withLock {
        isCurrentLocked(key, request)
    }

    private suspend fun isCurrent(key: Key, request: Uuid, message: UIMessage): Boolean = state.withLock {
        isCurrentLocked(key, request) && currentMessage(key)?.matchesTranslationSource(message) == true
    }

    private fun isCurrentLocked(key: Key, request: Uuid): Boolean = requests[key] == request

    private suspend fun release(key: Key, request: Uuid) = withContext(NonCancellable) {
        state.withLock {
            if (isCurrentLocked(key, request)) requests.remove(key)
        }
    }
}

internal fun UIMessage.matchesTranslationSource(expected: UIMessage): Boolean =
    id == expected.id && role == expected.role && parts == expected.parts

/** Merge only the translation of the unchanged source message, including an unselected alternative. */
fun Conversation.withMessageTranslation(expectedMessage: UIMessage, translation: String?): Conversation = copy(
    messageNodes = messageNodes.map { node ->
        node.copy(
            messages = node.messages.map { message ->
                if (message.matchesTranslationSource(expectedMessage)) {
                    message.copy(translation = translation)
                } else {
                    message
                }
            },
        )
    },
)
