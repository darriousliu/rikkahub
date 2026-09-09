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

class ConversationTitleGeneratorTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `blank title uses selected model prompt and saves trimmed response`() = runTest {
        val fixture = fixture(response = response("  Generated title  "))
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertSame(fixture.model, fixture.provider.params!!.model)
        assertTrue("Question" in fixture.provider.messages.single().toText())
        assertEquals("Generated title", fixture.saved.single().title)
    }

    @Test
    fun `existing title only generates when forced`() = runTest {
        val fixture = fixture(snapshot = conversation(title = "Existing"))
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(0, fixture.provider.calls)
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot, force = true)
        assertEquals(1, fixture.provider.calls)
    }

    @Test
    fun `falls back to fast model and skips when no model is available`() = runTest {
        val fallback = fixture(titleModelId = Uuid.random())
        fallback.generator.generate(fallback.snapshot.id, fallback.snapshot)
        assertSame(fallback.fastModel, fallback.provider.params!!.model)

        val missing = fixture(titleModelId = Uuid.random(), fastModelId = Uuid.random())
        missing.generator.generate(missing.snapshot.id, missing.snapshot)
        assertEquals(0, missing.provider.calls)
    }

    @Test
    fun `uses provider override and background model parameters`() = runTest {
        val headers = listOf(CustomHeader("X-Test", "header"))
        val bodies = listOf(CustomBody("test", JsonPrimitive("body")))
        val override = ProviderSetting.OpenAI(name = "Override", baseUrl = "https://override.invalid")
        val model = Model(
            "title", "Title", customHeaders = headers, customBodies = bodies, providerOverwrite = override,
        )
        val fixture = fixture(model = model)
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        assertEquals(ReasoningLevel.AUTO, fixture.provider.params!!.reasoningLevel)
        assertEquals(headers, fixture.provider.params!!.customHeaders)
        assertEquals(bodies, fixture.provider.params!!.customBody)
        assertEquals("Override", fixture.provider.setting!!.name)
    }

    @Test
    fun `uses selected latest four messages and truncates summaries`() = runTest {
        val nodes = listOf(
            UIMessage.user("excluded").toMessageNode(),
            MessageNode(
                messages = listOf(UIMessage.assistant("discarded"), UIMessage.assistant("one")),
                selectIndex = 1,
            ),
            UIMessage.user("two").toMessageNode(),
            UIMessage.assistant("three").toMessageNode(),
            UIMessage.user("x".repeat(600)).toMessageNode(),
        )
        val fixture = fixture(snapshot = Conversation.ofId(Uuid.random(), messages = nodes))
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot)
        val prompt = fixture.provider.messages.single().toText()
        assertTrue("excluded" !in prompt)
        assertTrue("discarded" !in prompt)
        assertTrue(prompt.startsWith("Test Locale\n[ASSISTANT]: one"))
        assertTrue("[USER]: ${"x".repeat(492)}..." in prompt)
    }

    @Test
    fun `blank response and rename during request are saved as original behavior`() = runTest {
        val fixture = fixture(snapshot = conversation(title = "Old"), response = response("   "))
        fixture.provider.beforeResponse = { fixture.database = fixture.database?.copy(title = "User rename") }
        fixture.generator.generate(fixture.snapshot.id, fixture.snapshot, force = true)
        assertEquals("", fixture.saved.single().title)
    }

    @Test
    fun `failure invokes error callback and deletion skips save`() = runTest {
        val failure = IllegalStateException("failed")
        val failed = fixture(failure = failure)
        failed.generator.generate(failed.snapshot.id, failed.snapshot)
        assertSame(failure, failed.error)

        val deleted = fixture()
        deleted.provider.beforeResponse = { deleted.database = null }
        deleted.generator.generate(deleted.snapshot.id, deleted.snapshot)
        assertTrue(deleted.saved.isEmpty())
    }

    @Test
    fun `empty choices and save failures report errors while null message saves empty title`() = runTest {
        val choices = fixture(response = MessageChunk(id = "response", model = "fake", choices = emptyList()))
        choices.generator.generate(choices.snapshot.id, choices.snapshot)
        assertTrue(choices.error is IndexOutOfBoundsException)
        assertTrue(choices.saved.isEmpty())

        val nullMessage = fixture(response = response(null))
        nullMessage.generator.generate(nullMessage.snapshot.id, nullMessage.snapshot)
        assertEquals("", nullMessage.saved.single().title)

        val saveFailure = fixture(onSave = { error("Save failed") })
        saveFailure.generator.generate(saveFailure.snapshot.id, saveFailure.snapshot)
        assertEquals("Save failed", saveFailure.error?.message)
        assertTrue(saveFailure.saved.isEmpty())

        val cancelled = fixture(failure = CancellationException("Cancelled"))
        cancelled.generator.generate(cancelled.snapshot.id, cancelled.snapshot)
        assertTrue(cancelled.error is CancellationException)
        assertTrue(cancelled.saved.isEmpty())
    }

    private fun fixture(
        snapshot: Conversation = conversation(),
        response: MessageChunk = response("New title"),
        failure: Throwable? = null,
        model: Model = Model(modelId = "title", displayName = "Title"),
        titleModelId: Uuid? = model.id,
        fastModelId: Uuid? = null,
        onSave: suspend (Conversation) -> Unit = {},
    ): Fixture {
        val fastModel = Model(modelId = "fast", displayName = "Fast")
        val provider = FakeProvider(response, failure)
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also { clients += it }
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val fixture = Fixture(snapshot, model, fastModel, provider)
        fixture.generator = ConversationTitleGenerator(
            providerManager = manager,
            getSettings = {
                Settings(
                    titleModelId = titleModelId,
                    fastModelId = fastModelId ?: fastModel.id,
                    titlePrompt = "{locale}\n{content}",
                    providers = listOf(ProviderSetting.OpenAI(models = listOf(model, fastModel))),
                )
            },
            getConversation = { fixture.database },
            saveConversation = { _, conversation ->
                onSave(conversation)
                fixture.saved += conversation
            },
            onError = { _, error -> fixture.error = error },
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
        lateinit var generator: ConversationTitleGenerator
        var database: Conversation? = snapshot
        val saved = mutableListOf<Conversation>()
        var error: Throwable? = null
    }

    private class FakeProvider(
        private val response: MessageChunk,
        private val failure: Throwable?,
    ) : Provider<ProviderSetting.OpenAI> {
        var calls = 0
        var params: TextGenerationParams? = null
        var setting: ProviderSetting.OpenAI? = null
        var messages = emptyList<UIMessage>()
        var beforeResponse: () -> Unit = {}

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls++
            setting = providerSetting
            this.messages = messages
            this.params = params
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
        fun conversation(title: String = "") = Conversation.ofId(
            Uuid.random(),
            messages = listOf(
                UIMessage.user("Question").toMessageNode(),
                UIMessage.assistant("Answer").toMessageNode(),
            ),
        ).copy(title = title)

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
