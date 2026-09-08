package me.rerere.rikkahub.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConversationSuggestionGeneratorTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `disabled suggestions do not clear request or save`() = runTest {
        val conversation = conversation(suggestions = listOf("Old"))
        val fixture = fixture(conversation, enabled = false)

        fixture.generator.generate(conversation.id, conversation)

        assertEquals(0, fixture.provider.calls.size)
        assertTrue(fixture.clears.isEmpty())
        assertTrue(fixture.saves.isEmpty())
        assertEquals(listOf("Old"), fixture.latest(conversation.id)?.chatSuggestions)
    }

    @Test
    fun `missing or invalid suggestion model falls back to fast model`() = runTest {
        listOf(null, Uuid.random()).forEach { suggestionModelId ->
            val conversation = conversation()
            val fixture = fixture(conversation, suggestionModelId = suggestionModelId)

            fixture.generator.generate(conversation.id, conversation)

            assertSame(fixture.fastModel, fixture.provider.calls.single().params.model)
        }
    }

    @Test
    fun `no selected or fallback model skips generation`() = runTest {
        val conversation = conversation(suggestions = listOf("Old"))
        val fixture = fixture(
            conversation,
            suggestionModelId = Uuid.random(),
            fastModelId = Uuid.random(),
        )

        fixture.generator.generate(conversation.id, conversation)

        assertTrue(fixture.provider.calls.isEmpty())
        assertTrue(fixture.clears.isEmpty())
        assertTrue(fixture.saves.isEmpty())
    }

    @Test
    fun `prompt uses locale selected branches and at most eight 500 character summaries`() = runTest {
        val nodes = buildList {
            add(UIMessage.user("excluded").toMessageNode())
            add(UIMessage.assistant("also excluded").toMessageNode())
            add(UIMessage.user("one").toMessageNode())
            add(UIMessage.assistant("two").toMessageNode())
            add(UIMessage.user("three").toMessageNode())
            add(UIMessage.assistant("four").toMessageNode())
            add(UIMessage.user("five").toMessageNode())
            add(UIMessage.assistant("six").toMessageNode())
            add(UIMessage.user("x".repeat(600)).toMessageNode())
            add(
                MessageNode(
                    messages = listOf(
                        UIMessage.assistant("discarded branch"),
                        UIMessage.assistant("selected branch"),
                    ),
                    selectIndex = 1,
                ),
            )
        }
        val conversation = conversation().copy(messageNodes = nodes)
        val fixture = fixture(
            conversation,
            prompt = "locale={locale}\ncontent={content}",
            localeName = "Test Locale",
        )

        fixture.generator.generate(conversation.id, conversation)

        val prompt = fixture.provider.calls.single().messages.single().toText()
        assertTrue(prompt.startsWith("locale=Test Locale\ncontent=[USER]: one"), prompt)
        assertTrue("excluded" !in prompt, prompt)
        assertTrue("also excluded" !in prompt, prompt)
        assertTrue("[USER]: ${"x".repeat(492)}..." in prompt, prompt)
        assertTrue("selected branch" in prompt, prompt)
        assertTrue("discarded branch" !in prompt, prompt)
    }

    @Test
    fun `passes background params and model provider override`() = runTest {
        val headers = listOf(CustomHeader("X-Test", "header"))
        val bodies = listOf(CustomBody("test", JsonPrimitive("body")))
        val override = ProviderSetting.OpenAI(name = "Override", baseUrl = "https://override.invalid")
        val model = Model(
            modelId = "suggestion",
            displayName = "Suggestion",
            customHeaders = headers,
            customBodies = bodies,
            providerOverwrite = override,
        )
        val conversation = conversation()
        val fixture = fixture(conversation, suggestionModel = model)

        fixture.generator.generate(conversation.id, conversation)

        val call = fixture.provider.calls.single()
        assertSame(model, call.params.model)
        assertEquals(ReasoningLevel.AUTO, call.params.reasoningLevel)
        assertEquals(headers, call.params.customHeaders)
        assertEquals(bodies, call.params.customBody)
        assertEquals("Override", call.setting.name)
        assertEquals("https://override.invalid", call.setting.baseUrl)
        assertTrue(call.setting.models.isEmpty())
    }

    @Test
    fun `parses lines preserving duplicates numbering and order with a ten item limit`() = runTest {
        val lines = listOf("  1. First  ", "Duplicate", "", "Duplicate") + (4..13).map { "Item $it" }
        val conversation = conversation()
        val fixture = fixture(conversation, response = response(lines.joinToString("\n")))

        fixture.generator.generate(conversation.id, conversation)

        assertEquals(
            listOf("1. First", "Duplicate", "Duplicate") + (4..10).map { "Item $it" },
            fixture.saves.single().suggestions,
        )
    }

    @Test
    fun `empty choices absent message and blank text clear old suggestions and save empty`() = runTest {
        val responses = listOf(
            MessageChunk(id = "response", model = "fake", choices = emptyList()),
            response(message = null),
            response(" \n  "),
        )

        responses.forEach { response ->
            val conversation = conversation(suggestions = listOf("Old"))
            val fixture = fixture(conversation, response = response)

            fixture.generator.generate(conversation.id, conversation)

            assertEquals(1, fixture.clears.size)
            assertEquals(emptyList(), fixture.saves.single().suggestions)
            assertEquals(emptyList(), fixture.latest(conversation.id)?.chatSuggestions)
        }
    }

    @Test
    fun `provider failure and cancellation clear old suggestions but do not save`() = runTest {
        listOf(IllegalStateException("failed"), CancellationException("cancelled")).forEach { failure ->
            val conversation = conversation(suggestions = listOf("Old"))
            val fixture = fixture(conversation, handler = { _, _, _ -> throw failure })

            val actual = runCatching {
                fixture.generator.generate(conversation.id, conversation)
            }.exceptionOrNull()

            if (failure is CancellationException) {
                assertIs<CancellationException>(actual)
            } else {
                assertSame(failure, actual)
            }
            assertEquals(1, fixture.clears.size)
            assertTrue(fixture.saves.isEmpty())
            assertEquals(emptyList(), fixture.latest(conversation.id)?.chatSuggestions)
        }
    }

    @Test
    fun `clear persistence failure propagates before provider request`() = runTest {
        val failure = IllegalStateException("clear failed")
        val conversation = conversation(suggestions = listOf("Old"))
        val fixture = fixture(conversation)
        fixture.clearFailure = failure

        val actual = runCatching {
            fixture.generator.generate(conversation.id, conversation)
        }.exceptionOrNull()

        assertSame(failure, actual)
        assertEquals(1, fixture.clears.size)
        assertTrue(fixture.provider.calls.isEmpty())
        assertTrue(fixture.saves.isEmpty())
        assertEquals(listOf("Old"), fixture.latest(conversation.id)?.chatSuggestions)
    }

    @Test
    fun `message changes or deletion during generation prevent saving`() = runTest {
        listOf(false, true).forEach { delete ->
            val conversation = conversation(suggestions = listOf("Old"))
            lateinit var fixture: Fixture
            fixture = fixture(conversation, handler = { _, _, _ ->
                if (delete) {
                    fixture.conversations[conversation.id] = null
                } else {
                    fixture.conversations[conversation.id] = fixture.latest(conversation.id)?.copy(
                        messageNodes = listOf(UIMessage.user("New message").toMessageNode()),
                    )
                }
                response("Stale")
            })

            fixture.generator.generate(conversation.id, conversation)

            assertEquals(1, fixture.clears.size)
            assertTrue(fixture.saves.isEmpty())
        }
    }

    @Test
    fun `disabling suggestions during generation prevents saving`() = runTest {
        val conversation = conversation(suggestions = listOf("Old"))
        lateinit var fixture: Fixture
        fixture = fixture(conversation, handler = { _, _, _ ->
            fixture.settings = fixture.settings.copy(enableSuggestion = false)
            response("Stale")
        })

        fixture.generator.generate(conversation.id, conversation)

        assertEquals(1, fixture.clears.size)
        assertTrue(fixture.saves.isEmpty())
        assertEquals(emptyList(), fixture.latest(conversation.id)?.chatSuggestions)
    }

    @Test
    fun `newer request for same conversation wins when older request returns last`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var callIndex = 0
        val conversation = conversation(suggestions = listOf("Old"))
        val fixture = fixture(conversation, handler = { _, _, _ ->
            when (++callIndex) {
                1 -> {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    response("Older")
                }

                2 -> {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                    response("Newer")
                }

                else -> error("Unexpected request")
            }
        })

        val older = async { fixture.generator.generate(conversation.id, conversation) }
        firstStarted.await()
        val newer = async { fixture.generator.generate(conversation.id, conversation) }
        secondStarted.await()
        releaseSecond.complete(Unit)
        newer.await()
        releaseFirst.complete(Unit)
        older.await()

        assertEquals(listOf(listOf("Newer")), fixture.saves.map { it.suggestions })
        assertEquals(listOf("Newer"), fixture.latest(conversation.id)?.chatSuggestions)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `new request clears only after an older in progress save and keeps the new result`() = runTest {
        val olderSaveStarted = CompletableDeferred<Unit>()
        val releaseOlderSave = CompletableDeferred<Unit>()
        var callIndex = 0
        val conversation = conversation(suggestions = listOf("Old"))
        val fixture = fixture(conversation, handler = { _, _, _ ->
            when (++callIndex) {
                1 -> response("Older")
                2 -> response("Newer")
                else -> error("Unexpected request")
            }
        })
        fixture.onSave = { call ->
            if (call.suggestions == listOf("Older")) {
                olderSaveStarted.complete(Unit)
                releaseOlderSave.await()
            }
        }

        val older = async { fixture.generator.generate(conversation.id, conversation) }
        olderSaveStarted.await()
        val newer = async { fixture.generator.generate(conversation.id, conversation) }
        runCurrent()
        assertEquals(1, fixture.clears.size)
        assertEquals(1, fixture.provider.calls.size)
        assertEquals(listOf<Mutation>(Mutation.Clear), fixture.mutations)

        releaseOlderSave.complete(Unit)
        older.await()
        newer.await()

        assertEquals(
            listOf(
                Mutation.Clear,
                Mutation.Save(listOf("Older")),
                Mutation.Clear,
                Mutation.Save(listOf("Newer")),
            ),
            fixture.mutations,
        )
        assertEquals(listOf("Newer"), fixture.latest(conversation.id)?.chatSuggestions)
    }

    @Test
    fun `requests for different conversations do not supersede each other`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val first = conversation(messages = listOf(UIMessage.user("First conversation")))
        val second = conversation(messages = listOf(UIMessage.user("Second conversation")))
        val fixture = fixture(first, additionalConversations = listOf(second), handler = { _, messages, _ ->
            when {
                "First conversation" in messages.single().toText() -> {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    response("First result")
                }

                else -> {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                    response("Second result")
                }
            }
        })

        val firstRequest = async { fixture.generator.generate(first.id, first) }
        val secondRequest = async { fixture.generator.generate(second.id, second) }
        firstStarted.await()
        secondStarted.await()
        releaseSecond.complete(Unit)
        secondRequest.await()
        releaseFirst.complete(Unit)
        firstRequest.await()

        assertEquals(listOf("First result"), fixture.latest(first.id)?.chatSuggestions)
        assertEquals(listOf("Second result"), fixture.latest(second.id)?.chatSuggestions)
        assertEquals(setOf(first.id, second.id), fixture.saves.map { it.id }.toSet())
    }

    private fun fixture(
        conversation: Conversation,
        additionalConversations: List<Conversation> = emptyList(),
        enabled: Boolean = true,
        suggestionModel: Model = Model(modelId = "suggestion", displayName = "Suggestion"),
        fastModel: Model = Model(modelId = "fast", displayName = "Fast"),
        suggestionModelId: Uuid? = suggestionModel.id,
        fastModelId: Uuid = fastModel.id,
        prompt: String = "{locale}\n{content}",
        localeName: String = "English (Test)",
        response: MessageChunk = response("New suggestion"),
        handler: (suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk)? = null,
    ): Fixture {
        val provider = FakeProvider(handler ?: { _, _, _ -> response })
        val providerSetting = ProviderSetting.OpenAI(models = listOf(suggestionModel, fastModel))
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also { clients += it }
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val fixture = Fixture(
            settings = Settings(
                enableSuggestion = enabled,
                suggestionModelId = suggestionModelId,
                fastModelId = fastModelId,
                suggestionPrompt = prompt,
                providers = listOf(providerSetting),
            ),
            suggestionModel = suggestionModel,
            fastModel = fastModel,
            provider = provider,
            conversations = mutableMapOf<Uuid, Conversation?>().apply {
                (listOf(conversation) + additionalConversations).forEach { put(it.id, it) }
            },
        )
        fixture.generator = ConversationSuggestionGenerator(
            providerManager = manager,
            getSettings = { fixture.settings },
            getConversation = { id -> fixture.latest(id) },
            clearSuggestions = { id, expectedMessages ->
                fixture.clears += ClearCall(id, expectedMessages)
                fixture.clearFailure?.let { throw it }
                fixture.latest(id)?.takeIf { it.currentMessages == expectedMessages }?.let { latest ->
                    fixture.conversations[id] = latest.copy(chatSuggestions = emptyList())
                    fixture.mutations += Mutation.Clear
                }
            },
            saveSuggestions = { id, expectedMessages, suggestions ->
                val call = SaveCall(id, expectedMessages, suggestions)
                fixture.onSave(call)
                fixture.latest(id)?.takeIf { it.currentMessages == expectedMessages }?.let { latest ->
                    fixture.saves += call
                    fixture.conversations[id] = latest.copy(chatSuggestions = suggestions)
                    fixture.mutations += Mutation.Save(suggestions)
                }
            },
            getLocaleName = { localeName },
        )
        return fixture
    }

    private class Fixture(
        var settings: Settings,
        val suggestionModel: Model,
        val fastModel: Model,
        val provider: FakeProvider,
        val conversations: MutableMap<Uuid, Conversation?>,
    ) {
        lateinit var generator: ConversationSuggestionGenerator
        val clears = mutableListOf<ClearCall>()
        val saves = mutableListOf<SaveCall>()
        val mutations = mutableListOf<Mutation>()
        var clearFailure: Throwable? = null
        var onSave: suspend (SaveCall) -> Unit = {}

        fun latest(id: Uuid): Conversation? = conversations[id]
    }

    private data class ClearCall(
        val id: Uuid,
        val expectedMessages: List<UIMessage>,
    )

    private data class SaveCall(
        val id: Uuid,
        val expectedMessages: List<UIMessage>,
        val suggestions: List<String>,
    )

    private sealed interface Mutation {
        data object Clear : Mutation
        data class Save(val suggestions: List<String>) : Mutation
    }

    private data class ProviderCall(
        val setting: ProviderSetting.OpenAI,
        val messages: List<UIMessage>,
        val params: TextGenerationParams,
    )

    private class FakeProvider(
        private val handler: suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk,
    ) : Provider<ProviderSetting.OpenAI> {
        val calls = mutableListOf<ProviderCall>()

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls += ProviderCall(providerSetting, messages, params)
            return handler(providerSetting, messages, params)
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> = emptyFlow()

        override suspend fun generateEmbedding(
            providerSetting: ProviderSetting.OpenAI,
            params: EmbeddingGenerationParams,
        ): EmbeddingGenerationResult = error("Unused")
    }

    private companion object {
        fun conversation(
            messages: List<UIMessage> = listOf(UIMessage.user("Question"), UIMessage.assistant("Answer")),
            suggestions: List<String> = emptyList(),
        ): Conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = messages.map(UIMessage::toMessageNode),
        ).copy(chatSuggestions = suggestions)

        fun response(message: String?): MessageChunk = MessageChunk(
            id = "response",
            model = "fake",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = message?.let(UIMessage::assistant),
                    finishReason = "stop",
                ),
            ),
        )
    }
}
