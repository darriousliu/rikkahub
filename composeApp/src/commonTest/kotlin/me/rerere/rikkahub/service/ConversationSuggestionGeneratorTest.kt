package me.rerere.rikkahub.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    fun `disabled setting does not update or request`() = runTest {
        val fixture = fixture(enabled = false)
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertTrue(fixture.provider.calls.isEmpty())
        assertTrue(fixture.updated.isEmpty())
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `uses fallback model prompt and saves parsed suggestions limited to ten`() = runTest {
        val fixture = fixture(
            suggestionModelId = Uuid.random(),
            response = response((1..12).joinToString("\n") { " Item $it " }),
        )
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)

        assertSame(fixture.fastModel, fixture.provider.calls.single().params.model)
        assertTrue("Question" in fixture.provider.calls.single().messages.single().toText())
        assertEquals(emptyList(), fixture.updated.single().chatSuggestions)
        assertEquals((1..10).map { "Item $it" }, fixture.saved.single().chatSuggestions)
    }

    @Test
    fun `uses passed snapshot content rather than later database content`() = runTest {
        val snapshot = conversation(messages = listOf(UIMessage.user("original")))
        val fixture = fixture(snapshot = snapshot)
        fixture.database = snapshot.copy(messageNodes = listOf(UIMessage.user("newer").toMessageNode()))
        fixture.generator.generate(snapshot.id, snapshot)
        val prompt = fixture.provider.calls.single().messages.single().toText()
        assertTrue("original" in prompt)
        assertTrue("newer" !in prompt)
    }

    @Test
    fun `empty response saves empty suggestions`() = runTest {
        val fixture = fixture(response = response(null))
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(emptyList(), fixture.saved.single().chatSuggestions)
    }

    @Test
    fun `failure clears only loaded conversation and preserves database suggestions`() = runTest {
        val fixture = fixture(
            snapshot = conversation(suggestions = listOf("Old")),
            handler = { _, _, _ -> throw IllegalStateException("failed") },
        )
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(emptyList(), fixture.loaded?.chatSuggestions)
        assertEquals(listOf("Old"), fixture.database?.chatSuggestions)
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `save uses database then loaded then snapshot`() = runTest {
        val fixture = fixture()
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(
            fixture.database,
            fixture.saved.single().copy(chatSuggestions = fixture.database!!.chatSuggestions),
        )

        fixture.database = null
        fixture.loaded = fixture.snapshot.copy(title = "Loaded")
        fixture.saved.clear()
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals("Loaded", fixture.saved.single().title)

        fixture.loaded = null
        fixture.saved.clear()
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(fixture.snapshot.title, fixture.saved.single().title)
    }

    @Test
    fun `selected model keeps override parameters and unavailable models skip all work`() = runTest {
        val model = Model(
            modelId = "suggestion",
            customHeaders = listOf(CustomHeader("X-Test", "header")),
            customBodies = listOf(CustomBody("test", JsonPrimitive("body"))),
            providerOverwrite = ProviderSetting.OpenAI(name = "Override"),
        )
        val fixture = fixture(model = model)
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        val call = fixture.provider.calls.single()
        assertEquals(model, call.params.model)
        assertEquals(ReasoningLevel.AUTO, call.params.reasoningLevel)
        assertEquals(model.customHeaders, call.params.customHeaders)
        assertEquals(model.customBodies, call.params.customBody)
        assertEquals("Override", call.setting.name)

        fixture.settings = fixture.settings.copy(suggestionModelId = Uuid.random(), fastModelId = Uuid.random())
        fixture.saved.clear()
        fixture.updated.clear()
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(1, fixture.provider.calls.size)
        assertTrue(fixture.saved.isEmpty())
        assertTrue(fixture.updated.isEmpty())
    }

    @Test
    fun `prompt uses selected latest eight messages with original truncation`() = runTest {
        val nodes = (1..9).map { UIMessage.user("message-$it").toMessageNode() }.toMutableList()
        nodes[1] = MessageNode(
            messages = listOf(UIMessage.user("discarded"), UIMessage.user("selected")),
            selectIndex = 1,
        )
        nodes[8] = UIMessage.user("x".repeat(600)).toMessageNode()
        val fixture = fixture(snapshot = Conversation.ofId(Uuid.random(), messages = nodes))
        fixture.provider.beforeResponse = { fixture.settings = fixture.settings.copy(enableSuggestion = false) }
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)

        val prompt = fixture.provider.calls.single().messages.single().toText()
        assertTrue(prompt.startsWith("Test Locale\n[USER]: selected"))
        assertTrue("message-1" !in prompt)
        assertTrue("discarded" !in prompt)
        assertTrue("[USER]: ${"x".repeat(492)}..." in prompt)
        assertEquals(listOf("First", "Second"), fixture.saved.single().chatSuggestions)
    }

    @Test
    fun `empty choices cancellation and save failure retain original error handling`() = runTest {
        val failures = listOf(
            fixture(response = MessageChunk(id = "empty", model = "fake", choices = emptyList())),
            fixture(handler = { _, _, _ -> throw CancellationException("cancelled") }),
            fixture(onSave = { error("save failed") }),
        )
        failures.forEach { fixture ->
            fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
            assertEquals(emptyList(), fixture.loaded?.chatSuggestions)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    private fun fixture(
        snapshot: Conversation = conversation(),
        enabled: Boolean = true,
        suggestionModelId: Uuid? = null,
        response: MessageChunk = response("First\nSecond"),
        model: Model = Model(modelId = "suggestion", displayName = "Suggestion"),
        onSave: suspend (Conversation) -> Unit = {},
        handler: (suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk)? = null,
    ): Fixture {
        val fast = Model(modelId = "fast", displayName = "Fast")
        val provider = FakeProvider(handler ?: { _, _, _ -> response })
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also { clients += it }
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val fixture = Fixture(snapshot, model, fast, provider)
        fixture.settings = Settings(
            enableSuggestion = enabled,
            suggestionModelId = suggestionModelId ?: model.id,
            fastModelId = fast.id,
            suggestionPrompt = "{locale}\n{content}",
            providers = listOf(ProviderSetting.OpenAI(models = listOf(model, fast))),
        )
        fixture.generator = ConversationSuggestionGenerator(
            providerManager = manager,
            getSettings = { fixture.settings },
            getConversation = { fixture.database },
            getLoadedConversation = { fixture.loaded },
            updateConversation = { _, conversation ->
                fixture.updated += conversation
                fixture.loaded = conversation
            },
            saveConversation = { _, conversation ->
                onSave(conversation)
                fixture.saved += conversation
            },
            getLocaleName = { "Test Locale" },
        )
        return fixture
    }

    private class Fixture(
        val snapshot: Conversation,
        val model: Model,
        val fastModel: Model,
        val provider: FakeProvider,
    ) {
        lateinit var generator: ConversationSuggestionGenerator
        lateinit var settings: Settings
        var database: Conversation? = snapshot
        var loaded: Conversation? = snapshot
        val updated = mutableListOf<Conversation>()
        val saved = mutableListOf<Conversation>()
    }

    private data class Call(
        val setting: ProviderSetting.OpenAI,
        val messages: List<UIMessage>,
        val params: TextGenerationParams,
    )

    private class FakeProvider(
        private val handler: suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk,
    ) : Provider<ProviderSetting.OpenAI> {
        val calls = mutableListOf<Call>()
        var beforeResponse: () -> Unit = {}

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls += Call(providerSetting, messages, params)
            beforeResponse()
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
        ) = Conversation.ofId(
            Uuid.random(),
            messages = messages.map(UIMessage::toMessageNode),
        ).copy(chatSuggestions = suggestions)

        fun response(message: String?) = MessageChunk(
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
