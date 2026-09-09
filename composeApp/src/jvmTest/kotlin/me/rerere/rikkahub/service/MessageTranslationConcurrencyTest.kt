package me.rerere.rikkahub.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MessageTranslationConcurrencyTest {
    @Test
    fun `clear cannot leave a partial callback that entered before its memory update`() {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val message = UIMessage.assistant("source")
        val conversationId = Uuid.random()
        val state = MutableStateFlow(Conversation.ofId(conversationId, messages = listOf(message.toMessageNode())))
        val partialUpdateEntered = CountDownLatch(1)
        val releasePartialUpdate = CountDownLatch(1)
        val disk = mutableMapOf<Uuid, String?>()

        val manager = MessageTranslationManager(
            scope = scope,
            getSettings = { Settings() },
            translateText = { _, _, _, _ -> flow { emit("partial") } },
            getConversation = { id -> state.value.takeIf { it.id == id } },
            updateTranslation = { _, expected, translation ->
                if (translation == "partial") {
                    partialUpdateEntered.countDown()
                    require(releasePartialUpdate.await(2, TimeUnit.SECONDS)) { "partial update was not released" }
                }
                state.value = state.value.withMessageTranslation(expected, translation)
            },
            saveTranslation = { _, expected, translation ->
                disk[expected.id] = translation
                true
            },
            getLoadingText = { "loading" },
            onError = { _, error -> throw AssertionError("unexpected translation error", error) },
        )

        var translationJob: Job? = null
        try {
            translationJob = manager.translate(conversationId, message, "en", "English")
            assertTrue(partialUpdateEntered.await(2, TimeUnit.SECONDS), "partial callback did not reach update")

            // The call returns a job without blocking this executor. Its memory update must queue
            // behind the partial callback, instead of clearing first and allowing a stale overwrite.
            val clearFuture = executor.submit<Job?> { manager.clear(conversationId, message.id) }
            val clearJob = clearFuture.get(2, TimeUnit.SECONDS) ?: error("clear unexpectedly skipped message")
            assertEquals("loading", translationOf(state.value, message.id))
            releasePartialUpdate.countDown()

            runBlocking {
                withTimeout(2_000) {
                    clearJob.join()
                    translationJob!!.join()
                }
            }

            assertNull(translationOf(state.value, message.id))
            assertNull(disk[message.id])
        } finally {
            releasePartialUpdate.countDown()
            translationJob?.cancel()
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "translation executor did not terminate")
        }
    }

    private fun translationOf(conversation: Conversation, messageId: Uuid): String? = conversation
        .getMessageNodeByMessageId(messageId)
        ?.messages
        ?.firstOrNull { it.id == messageId }
        ?.translation
}
