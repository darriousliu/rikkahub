package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MessageTranslationManagerTest {
    @Test
    fun `uses trimmed text parts separated by blank lines and ignores non text parts`() = runTest {
        val message = message(
            UIMessagePart.Image("file:///image"),
            UIMessagePart.Text(" first "),
            UIMessagePart.Reasoning("ignored"),
            UIMessagePart.Text(" second "),
        )
        val fixture = fixture(message) { _, source, code, name ->
            assertEquals("first \n\n second", source)
            assertEquals("zh_CN", code)
            assertEquals("Chinese", name)
            flowOf("译文")
        }

        fixture.manager.translate(fixture.id, message, "zh_CN", "Chinese")!!.join()

        assertEquals("译文", fixture.translation(message.id))
        assertEquals(listOf("译文"), fixture.savedTranslations)
    }

    @Test
    fun `non text and blank input do not start a request`() = runTest {
        val imageOnly = message(UIMessagePart.Image("file:///image"))
        val blank = message(UIMessagePart.Text("  \n "))
        val fixture = fixture(imageOnly) { _, _, _, _ -> error("must not translate") }

        assertNull(fixture.manager.translate(fixture.id, imageOnly, "en", "English"))
        fixture.state.value = conversation(fixture.id, blank)
        assertNull(fixture.manager.translate(fixture.id, blank, "en", "English"))
        assertTrue(fixture.updates.isEmpty())
        assertTrue(fixture.savedTranslations.isEmpty())
    }

    @Test
    fun `shows loading and streams full results before one final save`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(message) { _, _, _, _ -> flow {
            emit("one")
            emit("one two")
            release.await()
        } }

        val job = fixture.manager.translate(fixture.id, message, "en", "English")!!
        runCurrent()
        assertEquals(listOf("Translating", "one", "one two"), fixture.updates.take(3).map { it.second })
        assertTrue(fixture.savedTranslations.isEmpty())

        release.complete(Unit)
        job.join()
        assertEquals(listOf("one two"), fixture.savedTranslations)
        assertEquals("one two", fixture.diskTranslation(message.id))
    }

    @Test
    fun `updates only the target alternative and preserves other message fields`() = runTest {
        val target = message(UIMessagePart.Text("source"), translation = "old")
        val other = message(UIMessagePart.Text("other"), translation = "keep")
        val fixture = fixture(target, alternatives = listOf(other)) { _, _, _, _ -> flowOf("new") }

        fixture.manager.translate(fixture.id, target, "en", "English")!!.join()

        assertEquals("new", fixture.translation(target.id))
        assertEquals("keep", fixture.translation(other.id))
        assertEquals(listOf(other.id), fixture.otherMessageIds())
    }

    @Test
    fun `empty and blank streams clear loading and persist null`() = runTest {
        listOf<Flow<String>>(emptyFlow(), flowOf(" ", "\n")).forEach { response ->
            val message = message(UIMessagePart.Text("source"), translation = "old")
            val fixture = fixture(message) { _, _, _, _ -> response }

            fixture.manager.translate(fixture.id, message, "en", "English")!!.join()

            assertNull(fixture.translation(message.id))
            assertEquals(listOf<String?>(null), fixture.savedTranslations)
        }
    }

    @Test
    fun `failure clears owned value persists null and reports error`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val failure = IllegalStateException("network")
        val fixture = fixture(message) { _, _, _, _ -> flow { throw failure } }

        fixture.manager.translate(fixture.id, message, "en", "English")!!.join()

        assertNull(fixture.translation(message.id))
        assertEquals(listOf<String?>(null), fixture.savedTranslations)
        assertEquals(listOf(failure), fixture.errors)
    }

    @Test
    fun `cancellation clears owned value without reporting an error`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val fixture = fixture(message) { _, _, _, _ -> flow { throw CancellationException("stopped") } }

        val job = fixture.manager.translate(fixture.id, message, "en", "English")!!
        job.join()

        assertTrue(job.isCancelled)
        assertNull(fixture.translation(message.id))
        assertEquals(listOf<String?>(null), fixture.savedTranslations)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `clear persists immediately and a late chunk cannot restore translation`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(message) { _, _, _, _ -> flow {
            release.await()
            emit("late")
        } }

        val translate = fixture.manager.translate(fixture.id, message, "en", "English")!!
        runCurrent()
        fixture.manager.clear(fixture.id, message.id)!!.join()
        assertNull(fixture.translation(message.id))
        assertEquals(listOf<String?>(null), fixture.savedTranslations)

        release.complete(Unit)
        translate.join()
        assertNull(fixture.translation(message.id))
        assertEquals(listOf<String?>(null), fixture.savedTranslations)
    }

    @Test
    fun `newer request wins when older request fails later`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val releaseOld = CompletableDeferred<Unit>()
        var calls = 0
        val fixture = fixture(message) { _, _, _, _ ->
            if (calls++ == 0) flow {
                releaseOld.await()
                throw IllegalStateException("old failure")
            } else flowOf("new")
        }

        val old = fixture.manager.translate(fixture.id, message, "en", "English")!!
        runCurrent()
        fixture.manager.translate(fixture.id, message, "en", "English")!!.join()
        releaseOld.complete(Unit)
        old.join()

        assertEquals("new", fixture.translation(message.id))
        assertEquals(listOf("new"), fixture.savedTranslations)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `newer request wins when older request returns a result later`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val releaseOld = CompletableDeferred<Unit>()
        var calls = 0
        val fixture = fixture(message) { _, _, _, _ ->
            if (calls++ == 0) flow {
                releaseOld.await()
                emit("old")
            } else flowOf("new")
        }

        val old = fixture.manager.translate(fixture.id, message, "en", "English")!!
        runCurrent()
        fixture.manager.translate(fixture.id, message, "en", "English")!!.join()
        releaseOld.complete(Unit)
        old.join()

        assertEquals("new", fixture.translation(message.id))
        assertEquals(listOf("new"), fixture.savedTranslations)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `editing or deleting source while running prevents stale updates and persistence`() = runTest {
        listOf(true, false).forEach { editInsteadOfDelete ->
            val message = message(UIMessagePart.Text("source"))
            val release = CompletableDeferred<Unit>()
            val fixture = fixture(message) { _, _, _, _ -> flow {
                release.await()
                emit("stale")
            } }
            val job = fixture.manager.translate(fixture.id, message, "en", "English")!!
            runCurrent()
            fixture.state.value = if (editInsteadOfDelete) {
                conversation(fixture.id, message.copy(parts = listOf(UIMessagePart.Text("edited"))))
            } else {
                Conversation.ofId(fixture.id)
            }
            release.complete(Unit)
            job.join()

            assertTrue(fixture.savedTranslations.isEmpty())
            assertTrue(fixture.errors.isEmpty())
            assertNull(fixture.translation(message.id))
        }
    }

    @Test
    fun `in place edit retaining a partial translation clears only the owned value`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(message) { _, _, _, _ -> flow {
            emit("partial")
            release.await()
            emit("old final")
        } }

        val job = fixture.manager.translate(fixture.id, message, "en", "English")!!
        runCurrent()
        assertEquals("partial", fixture.translation(message.id))
        val edited = fixture.state.value.getMessageNodeByMessageId(message.id)!!.messages.single()
            .copy(parts = listOf(UIMessagePart.Text("edited")))
        fixture.state.value = conversation(fixture.id, edited)
        release.complete(Unit)
        job.join()

        assertNull(fixture.translation(message.id))
        assertEquals(listOf<String?>(null), fixture.savedTranslations)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `different messages run independently while persistence is serialized`() = runTest {
        val first = message(UIMessagePart.Text("first"))
        val second = message(UIMessagePart.Text("second"))
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        val secondSaveStarted = CompletableDeferred<Unit>()
        val fixture = fixture(first, alternatives = listOf(second), save = { _, message, translation ->
            if (message.id == first.id) {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            } else {
                secondSaveStarted.complete(Unit)
            }
            translation != "reject"
        }) { _, source, _, _ -> flowOf(if (source == "first") "one" else "two") }

        val firstJob = fixture.manager.translate(fixture.id, first, "en", "English")!!
        firstSaveStarted.await()
        val secondJob = fixture.manager.translate(fixture.id, second, "en", "English")!!
        runCurrent()
        assertFalse(secondSaveStarted.isCompleted)
        releaseFirstSave.complete(Unit)
        firstJob.join()
        secondJob.join()

        assertEquals("one", fixture.translation(first.id))
        assertEquals("two", fixture.translation(second.id))
        assertTrue(secondSaveStarted.isCompleted)
    }

    @Test
    fun `clear wins after an older save has entered the persistence mutex`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val oldSaveStarted = CompletableDeferred<Unit>()
        val releaseOldSave = CompletableDeferred<Unit>()
        val fixture = fixture(message, save = { _, _, translation ->
            if (translation == "old") {
                oldSaveStarted.complete(Unit)
                releaseOldSave.await()
            }
            true
        }) { _, _, _, _ -> flowOf("old") }

        val old = fixture.manager.translate(fixture.id, message, "en", "English")!!
        oldSaveStarted.await()
        val clear = fixture.manager.clear(fixture.id, message.id)!!
        assertNull(fixture.translation(message.id))
        releaseOldSave.complete(Unit)
        old.join()
        clear.join()

        assertNull(fixture.translation(message.id))
        assertNull(fixture.diskTranslation(message.id))
        assertEquals(listOf<String?>("old", null), fixture.savedTranslations)
    }

    @Test
    fun `rejected save removes result from memory`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val fixture = fixture(message, save = { _, _, _ -> false }) { _, _, _, _ -> flowOf("result") }

        fixture.manager.translate(fixture.id, message, "en", "English")!!.join()

        assertNull(fixture.translation(message.id))
        assertEquals(listOf("result"), fixture.savedTranslations)
    }

    private fun TestScope.fixture(
        message: UIMessage,
        alternatives: List<UIMessage> = emptyList(),
        save: suspend (Uuid, UIMessage, String?) -> Boolean = { _, _, _ -> true },
        translator: (Settings, String, String, String) -> Flow<String>,
    ): Fixture {
        val id = Uuid.random()
        val state = kotlinx.coroutines.flow.MutableStateFlow(conversation(id, message, alternatives))
        val saves = mutableListOf<String?>()
        val disk = mutableMapOf<Uuid, String?>()
        val updates = mutableListOf<Pair<Uuid, String?>>()
        val errors = mutableListOf<Throwable>()
        val manager = MessageTranslationManager(
            scope = this,
            getSettings = { Settings() },
            translateText = translator,
            getConversation = { requested -> state.value.takeIf { it.id == requested } },
            updateTranslation = { _, expected, translation ->
                updates += expected.id to translation
                state.value = state.value.withMessageTranslation(expected, translation)
            },
            saveTranslation = { conversationId, expected, translation ->
                saves += translation
                val accepted = save(conversationId, expected, translation)
                if (accepted) disk[expected.id] = translation
                accepted
            },
            getLoadingText = { "Translating" },
            onError = { _, error -> errors += error },
        )
        return Fixture(id, manager, state, saves, disk, updates, errors)
    }

    private fun conversation(id: Uuid, message: UIMessage, alternatives: List<UIMessage> = emptyList()): Conversation =
        Conversation.ofId(id, messages = listOf(MessageNode(messages = listOf(message) + alternatives)))

    private fun message(vararg parts: UIMessagePart, translation: String? = null): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = parts.toList(),
        translation = translation,
    )

    private class Fixture(
        val id: Uuid,
        val manager: MessageTranslationManager,
        val state: kotlinx.coroutines.flow.MutableStateFlow<Conversation>,
        val savedTranslations: List<String?>,
        private val disk: Map<Uuid, String?>,
        val updates: List<Pair<Uuid, String?>>,
        val errors: List<Throwable>,
    ) {
        fun translation(messageId: Uuid): String? = state.value.getMessageNodeByMessageId(messageId)
            ?.messages?.firstOrNull { it.id == messageId }?.translation

        fun diskTranslation(messageId: Uuid): String? = disk[messageId]

        fun otherMessageIds(): List<Uuid> = state.value.messageNodes.single().messages.drop(1).map { it.id }
    }
}
