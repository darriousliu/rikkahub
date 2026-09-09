package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MessageTranslationManagerTest {
    @Test
    fun `reads settings before skipping blank text`() = runTest {
        val message = message(UIMessagePart.Text("  "))
        val fixture = fixture(message)

        fixture.manager.translate(fixture.id, message, "en", "English").join()

        assertEquals(listOf("settings"), fixture.events)
        assertTrue(fixture.updates.isEmpty())
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `uses trimmed text parts and streams loading and translation`() = runTest {
        val message = message(
            UIMessagePart.Image("file:///image"),
            UIMessagePart.Text(" first "),
            UIMessagePart.Reasoning("ignored"),
            UIMessagePart.Text(" second "),
        )
        val fixture = fixture(message) { _, source, code, name, callback ->
            assertEquals("first \n\n second", source)
            assertEquals("zh_CN", code)
            assertEquals("Chinese", name)
            callback?.invoke("one")
            callback?.invoke("one two")
            flowOf("one", "one two")
        }

        fixture.manager.translate(fixture.id, message, "zh_CN", "Chinese").join()

        assertEquals(listOf("Translating", "one", "one two"), fixture.updates.map { it.second })
        assertEquals("one two", fixture.translation(message.id))
        assertEquals(listOf(fixture.state.value), fixture.saved)
    }

    @Test
    fun `completion saves the current conversation`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(message) { _, _, _, _, callback -> flow {
            callback?.invoke("translation")
            release.await()
            emit("translation")
        } }

        val job = fixture.manager.translate(fixture.id, message, "en", "English")
        runCurrent()
        val laterMessage = message(UIMessagePart.Text("added during translation"))
        fixture.state.value = fixture.state.value.copy(
            title = "Changed while translating",
            messageNodes = fixture.state.value.messageNodes + MessageNode.of(laterMessage),
        )
        release.complete(Unit)
        job.join()

        assertEquals(fixture.state.value, fixture.saved.single())
    }

    @Test
    fun `empty flow keeps loading text and saves the whole conversation`() = runTest {
        val message = message(UIMessagePart.Text("source"), translation = "old")
        val fixture = fixture(message)

        fixture.manager.translate(fixture.id, message, "en", "English").join()

        assertEquals("Translating", fixture.translation(message.id))
        assertEquals(listOf(fixture.state.value), fixture.saved)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `save failure clears memory and reports error`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val failure = IllegalStateException("save failed")
        val fixture = fixture(
            message = message,
            translator = { _, _, _, _, callback ->
                callback?.invoke("translated")
                flowOf("translated")
            },
            onSave = { throw failure },
        )

        fixture.manager.translate(fixture.id, message, "en", "English").join()

        assertNull(fixture.translation(message.id))
        assertEquals(listOf(failure), fixture.errors)
    }

    @Test
    fun `failure and cancellation clear memory and report error without saving`() = runTest {
        listOf(
            IllegalStateException("network"),
            kotlinx.coroutines.CancellationException("cancelled"),
        ).forEach { failure ->
            val message = message(UIMessagePart.Text("source"), translation = "old")
            val fixture = fixture(message) { _, _, _, _, _ -> flow { throw failure } }

            fixture.manager.translate(fixture.id, message, "en", "English").join()

            assertNull(fixture.translation(message.id))
            assertEquals(listOf(failure), fixture.errors)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test
    fun `clear only updates memory and does not invalidate a running request`() = runTest {
        val message = message(UIMessagePart.Text("source"))
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(message) { _, _, _, _, callback -> flow {
            release.await()
            callback?.invoke("late")
            emit("late")
        } }

        val job = fixture.manager.translate(fixture.id, message, "en", "English")
        runCurrent()
        fixture.manager.clear(fixture.id, message.id)
        assertNull(fixture.translation(message.id))
        assertTrue(fixture.saved.isEmpty())

        release.complete(Unit)
        job.join()
        assertEquals("late", fixture.translation(message.id))
        assertEquals(listOf(fixture.state.value), fixture.saved)
    }

    @Test
    fun `updates a message in an unselected branch by id`() = runTest {
        val target = message(UIMessagePart.Text("source"))
        val selected = message(UIMessagePart.Text("selected"))
        val fixture = fixture(target, alternatives = listOf(selected)) { _, _, _, _, callback ->
            callback?.invoke("translated")
            flowOf("translated")
        }

        fixture.manager.translate(fixture.id, target, "en", "English").join()

        assertEquals("translated", fixture.translation(target.id))
        assertNull(fixture.translation(selected.id))
    }

    private fun TestScope.fixture(
        message: UIMessage,
        alternatives: List<UIMessage> = emptyList(),
        onSave: suspend (Conversation) -> Unit = {},
        translator: (Settings, String, String, String, ((String) -> Unit)?) -> Flow<String> =
            { _, _, _, _, _ -> flowOf() },
    ): Fixture {
        val id = Uuid.random()
        val state = kotlinx.coroutines.flow.MutableStateFlow(conversation(id, message, alternatives))
        val events = mutableListOf<String>()
        val updates = mutableListOf<Pair<Uuid, String?>>()
        val saved = mutableListOf<Conversation>()
        val errors = mutableListOf<Throwable>()
        val manager = MessageTranslationManager(
            scope = this,
            getSettings = { events += "settings"; Settings() },
            translateText = translator,
            getConversation = { state.value },
            updateConversation = { _, conversation ->
                val previous = state.value
                state.value = conversation
                conversation.messageNodes.flatMap { it.messages }.forEach { updated ->
                    val before = previous.messageNodes.flatMap { it.messages }
                        .firstOrNull { it.id == updated.id }
                    if (before?.translation != updated.translation) updates += updated.id to updated.translation
                }
            },
            saveConversation = { _, conversation ->
                onSave(conversation)
                saved += conversation
            },
            getLoadingText = { "Translating" },
            onError = { _, error -> errors += error },
        )
        return Fixture(id, manager, state, events, updates, saved, errors)
    }

    private fun conversation(id: Uuid, message: UIMessage, alternatives: List<UIMessage>): Conversation =
        Conversation.ofId(
            id,
            messages = listOf(
                MessageNode(
                    messages = listOf(message) + alternatives,
                    selectIndex = if (alternatives.isEmpty()) 0 else 1,
                ),
            ),
        )

    private fun message(vararg parts: UIMessagePart, translation: String? = null) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = parts.toList(),
        translation = translation,
    )

    private class Fixture(
        val id: Uuid,
        val manager: MessageTranslationManager,
        val state: kotlinx.coroutines.flow.MutableStateFlow<Conversation>,
        val events: List<String>,
        val updates: List<Pair<Uuid, String?>>,
        val saved: List<Conversation>,
        val errors: List<Throwable>,
    ) {
        fun translation(messageId: Uuid): String? = state.value.messageNodes.flatMap { it.messages }
            .firstOrNull { it.id == messageId }?.translation
    }
}
