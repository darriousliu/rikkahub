package me.rerere.rikkahub.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConversationCompressorTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach(HttpClient::close)
        clients.clear()
    }

    @Test
    fun `uses configured model with custom request settings and all prompt placeholders`() = runTest {
        val headers = listOf(CustomHeader("X-Compress", "yes"))
        val bodies = listOf(CustomBody("temperature", JsonPrimitive("0")))
        val model = Model("compress", "Compress", customHeaders = headers, customBodies = bodies)
        val fixture = fixture(
            model = model,
            prompt = "{content}|{target_tokens}|{additional_context}|{locale}",
            messages = listOf(UIMessage.user("x".repeat(2_100)), UIMessage.assistant("recent")),
            locale = "Test locale",
        )

        assertTrue(fixture.compress(additional = "focus", target = 321, keep = 1).isSuccess)

        assertSame(model, fixture.provider.lastParams?.model)
        assertEquals(ReasoningLevel.AUTO, fixture.provider.lastParams?.reasoningLevel)
        assertEquals(headers, fixture.provider.lastParams?.customHeaders)
        assertEquals(bodies, fixture.provider.lastParams?.customBody)
        assertTrue(fixture.provider.lastPrompt().contains(
            "${"x".repeat(1992)}...|321|Additional instructions from user: focus|Test locale",
        ))
        assertEquals(listOf("summary", "recent"), fixture.savedMessages())
    }

    @Test
    fun `missing configured model falls back to chat model and uses its provider override`() = runTest {
        val model = Model(
            "chat", "Chat",
            providerOverwrite = ProviderSetting.OpenAI(name = "Override", baseUrl = "https://override.invalid"),
        )
        val fixture = fixture(model = model, compressModelId = Uuid.random())

        assertTrue(fixture.compress().isSuccess)
        assertSame(model, fixture.provider.lastParams?.model)
        assertEquals("Override", fixture.provider.lastSetting?.name)
        assertTrue(fixture.provider.lastSetting?.models?.isEmpty() == true)
    }

    @Test
    fun `summarizes selected branch and reconstructs retained messages while clearing suggestions`() = runTest {
        val retained = UIMessage.assistant("keep").copy(usage = TokenUsage(completionTokens = 9), translation = "保留")
        val conversation = conversation().copy(
            messageNodes = listOf(
                MessageNode(messages = listOf(UIMessage.user("discarded"), UIMessage.user("selected")), selectIndex = 1),
                retained.toMessageNode(),
            ),
            chatSuggestions = listOf("stale suggestion"),
        )
        val fixture = fixture(conversation = conversation)

        assertTrue(fixture.compress(keep = 1).isSuccess)

        assertTrue("selected" in fixture.provider.lastPrompt())
        assertFalse("discarded" in fixture.provider.lastPrompt())
        assertEquals(emptyList(), fixture.saved!!.chatSuggestions)
        assertEquals("summary", fixture.savedMessages()[0])
        assertEquals(retained, fixture.saved!!.messageNodes[1].currentMessage)
        assertEquals(0, fixture.saved!!.messageNodes[1].selectIndex)
    }

    @Test
    fun `keep boundaries retain original Android behavior`() = runTest {
        val insufficient = fixture(messages = listOf(UIMessage.user("only")))
        assertTrue(insufficient.compress(keep = 1).isFailure)
        assertEquals(0, insufficient.provider.calls)

        listOf(0, -1).forEach { keep ->
            val all = fixture(messages = emptyList())
            assertTrue(all.compress(target = 0, keep = keep).isSuccess)
            assertEquals(1, all.provider.calls)
            assertEquals(listOf("summary"), all.savedMessages())
        }
    }

    @Test
    fun `257 messages split recursively and summaries retain source order despite completion order`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()
        val fixture = fixture(messages = (0 until 257).map { UIMessage.user("m${it.toString().padStart(3, '0')}") })
        fixture.provider.responseForPrompt = { prompt ->
            if ("m000" in prompt) {
                firstStarted.complete(Unit)
                secondFinished.await()
                "first"
            } else {
                firstStarted.await()
                secondFinished.complete(Unit)
                "second"
            }
        }

        assertTrue(fixture.compress(keep = 0).isSuccess)
        assertEquals(2, fixture.provider.calls)
        assertEquals(listOf("first", "second"), fixture.savedMessages())
    }

    @Test
    fun `provider failure and cancellation become failed results without saving`() = runTest {
        listOf<Throwable>(IllegalStateException("network"), CancellationException("cancelled")).forEach { failure ->
            val fixture = fixture()
            fixture.provider.responseForPrompt = { throw failure }

            val result = fixture.compress()

            assertTrue(result.isFailure)
            assertEquals(failure::class, result.exceptionOrNull()!!::class)
            assertEquals(failure.message, result.exceptionOrNull()?.message)
            assertEquals(null, fixture.saved)
        }
    }

    @Test
    fun `empty choices and null message fail while blank summary is saved`() = runTest {
        listOf(EmptyChoices, null).forEach { response ->
            val fixture = fixture()
            fixture.provider.responseForPrompt = { response }
            assertTrue(fixture.compress().isFailure)
            assertEquals(null, fixture.saved)
        }

        val blank = fixture()
        blank.provider.responseForPrompt = { "   " }
        assertTrue(blank.compress().isSuccess)
        assertEquals(listOf("", "recent"), blank.savedMessages())
    }

    @Test
    fun `save errors become failures and save uses the supplied conversation snapshot`() = runTest {
        val failure = IllegalStateException("disk full")
        val failed = fixture(saveFailure = failure)
        assertSame(failure, failed.compress().exceptionOrNull())

        val original = conversation().copy(title = "original")
        val fixture = fixture(conversation = original)
        var external = original
        fixture.provider.beforeResponse = { external = original.copy(title = "changed elsewhere") }

        assertTrue(fixture.compress().isSuccess)
        assertEquals("changed elsewhere", external.title)
        assertEquals("original", fixture.saved!!.title)
    }

    private fun fixture(
        conversation: Conversation = conversation(),
        messages: List<UIMessage>? = null,
        model: Model = Model("compress", "Compress"),
        compressModelId: Uuid = model.id,
        chatModelId: Uuid = model.id,
        prompt: String = "{content}",
        locale: String = "English (Test)",
        saveFailure: Throwable? = null,
    ): Fixture {
        val source = messages?.let(::conversation) ?: conversation
        val provider = FakeProvider()
        val settings = Settings(
            compressModelId = compressModelId,
            chatModelId = chatModelId,
            compressPrompt = prompt,
            providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
        )
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also(clients::add)
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        return Fixture(source, provider).also { fixture ->
            fixture.compressor = ConversationCompressor(
                providerManager = manager,
                getSettings = { settings },
                saveConversation = { id, saved ->
                    assertEquals(source.id, id)
                    saveFailure?.let { throw it }
                    fixture.saved = saved
                },
                getNotEnoughMessagesText = { "not enough" },
                getLocaleName = { locale },
            )
        }
    }

    private class Fixture(val conversation: Conversation, val provider: FakeProvider) {
        lateinit var compressor: ConversationCompressor
        var saved: Conversation? = null

        suspend fun compress(additional: String = "", target: Int = 100, keep: Int = 1): Result<Unit> =
            compressor.compress(conversation.id, conversation, additional, target, keep)

        fun savedMessages(): List<String> = saved!!.currentMessages.map(UIMessage::toText)
    }

    private class FakeProvider : Provider<ProviderSetting.OpenAI> {
        var calls = 0
        var lastSetting: ProviderSetting.OpenAI? = null
        var lastParams: TextGenerationParams? = null
        private val prompts = mutableListOf<String>()
        var beforeResponse: () -> Unit = {}
        var responseForPrompt: suspend (String) -> Any? = { "summary" }

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls++
            lastSetting = providerSetting
            lastParams = params
            val prompt = messages.single().toText()
            prompts += prompt
            beforeResponse()
            return when (val response = responseForPrompt(prompt)) {
                EmptyChoices -> MessageChunk(id = "response", model = "fake", choices = emptyList())
                else -> MessageChunk(
                    id = "response",
                    model = "fake",
                    choices = listOf(UIMessageChoice(0, null, (response as String?)?.let(UIMessage::assistant), "stop")),
                )
            }
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

        fun lastPrompt(): String = prompts.last()
    }

    private data object EmptyChoices

    private companion object {
        fun conversation(
            messages: List<UIMessage> = listOf(UIMessage.user("old"), UIMessage.assistant("recent")),
        ): Conversation = Conversation.ofId(Uuid.random(), messages = messages.map(UIMessage::toMessageNode))
    }
}
