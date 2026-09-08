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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConversationTitleGeneratorTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `uses title model and saves trimmed title`() = runTest {
        val fixture = fixture(response = response("  Generated title  "))

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        assertSame(fixture.titleModel, fixture.provider.lastParams?.model)
        assertEquals("Generated title", fixture.saved?.title)
    }

    @Test
    fun `falls back to fast model when title model is missing`() = runTest {
        listOf(null, Uuid.random()).forEach { titleModelId ->
            val fixture = fixture(titleModelId = titleModelId)

            fixture.generator.generate(fixture.conversation.id, fixture.conversation)

            assertSame(fixture.fastModel, fixture.provider.lastParams?.model)
            assertEquals(1, fixture.provider.calls)
        }
    }

    @Test
    fun `returns without a request when neither selected model exists`() = runTest {
        val fixture = fixture(
            titleModelId = Uuid.random(),
            fastModelId = Uuid.random(),
        )

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        assertEquals(0, fixture.provider.calls)
        assertNull(fixture.saved)
    }

    @Test
    fun `existing title skips generation unless forced`() = runTest {
        val conversation = conversation(title = "Existing")
        val fixture = fixture(conversation = conversation)

        fixture.generator.generate(conversation.id, conversation)
        assertEquals(0, fixture.settingsReads)
        assertEquals(0, fixture.provider.calls)

        fixture.generator.generate(conversation.id, conversation, force = true)
        assertEquals(1, fixture.provider.calls)
        assertEquals("New title", fixture.saved?.title)
    }

    @Test
    fun `prompt substitutes locale and summaries of only the latest four messages`() = runTest {
        val messages = listOf(
            UIMessage.user("excluded"),
            UIMessage.assistant("one"),
            UIMessage.user("two"),
            UIMessage.assistant("three"),
            UIMessage.user("x".repeat(600)),
        )
        val fixture = fixture(
            conversation = conversation(messages = messages),
            titlePrompt = "locale={locale}\ncontent={content}",
            localeName = "Test Locale",
        )

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        val prompt = fixture.provider.lastMessages.single().toText()
        assertTrue(prompt.startsWith("locale=Test Locale\ncontent=[ASSISTANT]: one"), prompt)
        assertTrue("[USER]: two" in prompt, prompt)
        assertTrue("[ASSISTANT]: three" in prompt, prompt)
        assertTrue("[USER]: ${"x".repeat(492)}..." in prompt, prompt)
        assertTrue("excluded" !in prompt, prompt)
    }

    @Test
    fun `passes background params with auto reasoning and model customizations`() = runTest {
        val headers = listOf(CustomHeader("X-Test", "header"))
        val bodies = listOf(CustomBody("test", JsonPrimitive("body")))
        val titleModel = Model(
            modelId = "title",
            displayName = "Title",
            customHeaders = headers,
            customBodies = bodies,
        )
        val fixture = fixture(titleModel = titleModel)

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        val params = fixture.provider.lastParams
        assertEquals(ReasoningLevel.AUTO, params?.reasoningLevel)
        assertEquals(headers, params?.customHeaders)
        assertEquals(bodies, params?.customBody)
    }

    @Test
    fun `uses model provider override`() = runTest {
        val override = ProviderSetting.OpenAI(name = "Override", baseUrl = "https://override.invalid")
        val titleModel = Model(modelId = "title", displayName = "Title", providerOverwrite = override)
        val fixture = fixture(titleModel = titleModel)

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        assertEquals("Override", fixture.provider.lastSetting?.name)
        assertEquals("https://override.invalid", fixture.provider.lastSetting?.baseUrl)
        assertTrue(fixture.provider.lastSetting?.models?.isEmpty() == true)
    }

    @Test
    fun `blank or absent response does not save`() = runTest {
        val responses = listOf(
            response("   "),
            response(message = null),
            MessageChunk(id = "response", model = "fake", choices = emptyList()),
        )

        responses.forEach { response ->
            val fixture = fixture(conversation = conversation(title = "Keep title"), response = response)
            fixture.generator.generate(fixture.conversation.id, fixture.conversation, force = true)
            assertNull(fixture.saved)
            assertEquals("Keep title", fixture.latest?.title)
        }
    }

    @Test
    fun `provider failure propagates without saving`() = runTest {
        val expected = IllegalStateException("provider failed")
        val fixture = fixture(providerFailure = expected)

        val actual = runCatching {
            fixture.generator.generate(fixture.conversation.id, fixture.conversation)
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertNull(fixture.saved)
    }

    @Test
    fun `cancellation propagates without saving`() = runTest {
        val fixture = fixture(providerFailure = CancellationException("cancelled"))

        val actual = runCatching {
            fixture.generator.generate(fixture.conversation.id, fixture.conversation)
        }.exceptionOrNull()

        assertIs<CancellationException>(actual)
        assertNull(fixture.saved)
    }

    @Test
    fun `storage failure propagates`() = runTest {
        val expected = IllegalStateException("storage failed")
        val fixture = fixture(saveFailure = expected)

        val actual = runCatching {
            fixture.generator.generate(fixture.conversation.id, fixture.conversation)
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertNull(fixture.saved)
    }

    @Test
    fun `prompt includes only the selected message branch`() = runTest {
        val conversation = conversation().copy(
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(UIMessage.assistant("discarded branch"), UIMessage.assistant("selected branch")),
                    selectIndex = 1,
                ),
            ),
        )
        val fixture = fixture(conversation = conversation)

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        val prompt = fixture.provider.lastMessages.single().toText()
        assertTrue("selected branch" in prompt, prompt)
        assertTrue("discarded branch" !in prompt, prompt)
    }

    @Test
    fun `does not overwrite title changed during request even when forced`() = runTest {
        val conversation = conversation(title = "Old title")
        val fixture = fixture(conversation = conversation)
        fixture.provider.beforeResponse = {
            fixture.latest = fixture.latest?.copy(title = "User rename")
        }

        fixture.generator.generate(conversation.id, conversation, force = true)

        assertNull(fixture.saved)
        assertEquals("User rename", fixture.latest?.title)
    }

    @Test
    fun `does not recreate conversation deleted during request`() = runTest {
        val fixture = fixture()
        fixture.provider.beforeResponse = { fixture.latest = null }

        fixture.generator.generate(fixture.conversation.id, fixture.conversation)

        assertNull(fixture.saved)
    }

    private fun fixture(
        conversation: Conversation = conversation(),
        titleModel: Model = Model(modelId = "title", displayName = "Title"),
        fastModel: Model = Model(modelId = "fast", displayName = "Fast"),
        titleModelId: Uuid? = titleModel.id,
        fastModelId: Uuid = fastModel.id,
        titlePrompt: String = "{locale}\n{content}",
        localeName: String = "English (Test)",
        response: MessageChunk = response("New title"),
        providerFailure: Throwable? = null,
        saveFailure: Throwable? = null,
    ): Fixture {
        val provider = FakeProvider(response, providerFailure)
        val providerSetting = ProviderSetting.OpenAI(models = listOf(titleModel, fastModel))
        val settings = Settings(
            titleModelId = titleModelId,
            fastModelId = fastModelId,
            titlePrompt = titlePrompt,
            providers = listOf(providerSetting),
        )
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also { clients += it }
        val manager = ProviderManager(client).apply {
            registerProvider("openai", provider)
        }
        val fixture = Fixture(
            conversation = conversation,
            titleModel = titleModel,
            fastModel = fastModel,
            provider = provider,
            latest = conversation,
        )
        fixture.generator = ConversationTitleGenerator(
            providerManager = manager,
            getSettings = {
                fixture.settingsReads++
                settings
            },
            getConversation = { fixture.latest },
            saveTitle = { id, expectedTitle, title ->
                assertEquals(conversation.id, id)
                assertEquals(fixture.latest?.title, expectedTitle)
                saveFailure?.let { throw it }
                fixture.saved = fixture.latest?.copy(title = title)
            },
            getLocaleName = { localeName },
        )
        return fixture
    }

    private class Fixture(
        val conversation: Conversation,
        val titleModel: Model,
        val fastModel: Model,
        val provider: FakeProvider,
        var latest: Conversation?,
    ) {
        lateinit var generator: ConversationTitleGenerator
        var saved: Conversation? = null
        var settingsReads: Int = 0
    }

    private class FakeProvider(
        private val response: MessageChunk,
        private val failure: Throwable?,
    ) : Provider<ProviderSetting.OpenAI> {
        var calls: Int = 0
        var lastSetting: ProviderSetting.OpenAI? = null
        var lastMessages: List<UIMessage> = emptyList()
        var lastParams: TextGenerationParams? = null
        var beforeResponse: () -> Unit = {}

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls++
            lastSetting = providerSetting
            lastMessages = messages
            lastParams = params
            beforeResponse()
            failure?.let { throw it }
            return response
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
            title: String = "",
            messages: List<UIMessage> = listOf(UIMessage.user("Question"), UIMessage.assistant("Answer")),
        ): Conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = messages.map(UIMessage::toMessageNode),
        ).copy(title = title)

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
