package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.platform.PlatformDeviceInfo
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class ChatServiceBusinessTest {
    @Test
    fun `title keeps fallback override parameters selected messages and concurrent metadata`() = scenario { f, p ->
        val override = ProviderSetting.OpenAI(name = "Override")
        val model = Model("title", "Title", providerOverwrite = override,
            customHeaders = listOf(CustomHeader("X-Test", "header")),
            customBodies = listOf(CustomBody("test", JsonPrimitive("body"))))
        f.configure(settings(model).copy(titleModelId = Uuid.random(), fastModelId = model.id))
        val original = conversation().copy(title = "")
        f.load(original)
        p.generate = { call ->
            assertEquals("Override", call.setting.name)
            assertEquals(model.customHeaders, call.params.customHeaders)
            assertEquals(model.customBodies, call.params.customBody)
            assertEquals(ReasoningLevel.AUTO, call.params.reasoningLevel)
            assertTrue(call.messages.single().toText().startsWith(PlatformDeviceInfo.localeName))
            f.repository.updateConversation(original.copy(title = "rename", isPinned = true))
            response("  generated  ")
        }
        f.service.generateTitle(original.id, original)
        val saved = f.repository.getConversationById(original.id)!!
        assertEquals("generated", saved.title)
        assertTrue(saved.isPinned)
        f.service.generateTitle(saved.id, saved)
        assertEquals(1, p.calls.size)
        f.service.generateTitle(saved.id, saved, force = true)
        assertEquals(2, p.calls.size)
    }

    @Test
    fun `title keeps blank response error and cancellation handling`() = scenario { f, p ->
        val original = conversation().copy(title = "")
        f.load(original)
        p.generate = { response(" ") }
        f.service.generateTitle(original.id, original)
        assertEquals("", f.repository.getConversationById(original.id)?.title)
        p.generate = { response(null).copy(choices = emptyList()) }
        f.service.generateTitle(original.id, original)
        assertIs<IndexOutOfBoundsException>(f.service.errors.value.single().error)
        f.service.clearAllErrors()
        p.generate = { throw CancellationException("cancelled") }
        f.service.generateTitle(original.id, original)
        assertTrue(f.service.errors.value.isEmpty())
        f.configure(f.settings.settingsFlow.value.copy(titleModelId = Uuid.random(), fastModelId = Uuid.random()))
        val calls = p.calls.size
        f.service.generateTitle(original.id, original)
        assertEquals(calls, p.calls.size)
    }

    @Test
    fun `suggestions use original snapshot and save latest metadata with ten trimmed lines`() = scenario { f, p ->
        val original = conversation()
        f.load(original)
        f.configure(f.settings.settingsFlow.value.copy(enableSuggestion = false))
        f.service.generateSuggestion(original.id, original)
        assertTrue(p.calls.isEmpty())
        f.configure(f.settings.settingsFlow.value.copy(enableSuggestion = true))
        p.generate = { call ->
            assertTrue("oldmarker" in call.messages.single().toText())
            assertTrue(f.service.getConversationFlow(original.id).value.chatSuggestions.isEmpty())
            f.repository.updateConversation(original.copy(title = "latest"))
            response((1..12).joinToString("\n\n") { " suggestion $it " })
        }
        f.service.generateSuggestion(original.id, original)
        val saved = f.repository.getConversationById(original.id)!!
        assertEquals("latest", saved.title)
        assertEquals((1..10).map { "suggestion $it" }, saved.chatSuggestions)
        p.generate = { error("failed") }
        f.service.generateSuggestion(original.id, saved)
        assertEquals(saved, f.repository.getConversationById(original.id))
        assertTrue(f.service.getConversationFlow(original.id).value.chatSuggestions.isEmpty())
        assertTrue(f.service.errors.value.isEmpty())
    }

    @Test
    fun `compression saves original metadata recent message and matching FTS entries`() = scenario { f, p ->
        val original = conversation()
        f.load(original)
        p.generate = { call ->
            assertTrue("oldmarker" in call.messages.single().toText())
            assertFalse("recentmarker" in call.messages.single().toText())
            assertTrue("500" in call.messages.single().toText())
            assertTrue("Additional instructions from user: concise" in call.messages.single().toText())
            response(" compressedmarker ")
        }
        assertTrue(f.service.compressConversation(original.id, original, "concise", 500, 1).isSuccess)
        val saved = f.repository.getConversationById(original.id)!!
        assertEquals(listOf("compressedmarker", "recentmarker"), saved.currentMessages.map(UIMessage::toText))
        assertEquals(original.currentMessages.last(), saved.currentMessages.last())
        assertEquals(original.copy(messageNodes = saved.messageNodes, chatSuggestions = emptyList()), saved)
        assertEquals(saved, f.service.getConversationFlow(original.id).value)
        assertTrue(f.fts.search("oldmarker").isEmpty())
        assertEquals(1, f.fts.search("compressedmarker").size)
    }

    @Test
    fun `compression failure cancellation and insufficient messages leave SQLite unchanged`() = scenario { f, p ->
        val original = conversation()
        f.load(original)
        assertTrue(f.service.compressConversation(original.id, original, "", 500, 2).isFailure)
        assertTrue(p.calls.isEmpty())
        for (failure in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
            p.generate = { throw failure }
            val actual = f.service.compressConversation(original.id, original, "", 500, 1).exceptionOrNull()!!
            assertEquals(failure::class, actual::class)
            assertEquals(failure.message, actual.message)
            assertEquals(original, f.repository.getConversationById(original.id))
        }
        p.generate = { response(null) }
        assertTrue(f.service.compressConversation(original.id, original, "", 500, 1).isFailure)
        assertEquals(original, f.repository.getConversationById(original.id))
    }

    @Test
    fun `257 message compression runs both chunks concurrently and preserves their source order`() = scenario { f, p ->
        val original = conversation().copy(messageNodes = (0..256).map { UIMessage.user("marker$it;").toMessageNode() })
        f.load(original)
        val secondEntered = CompletableDeferred<Unit>()
        p.generate = { call ->
            if ("marker0;" in call.messages.single().toText()) {
                secondEntered.await()
                response("first summary")
            } else {
                secondEntered.complete(Unit)
                response("second summary")
            }
        }
        assertTrue(f.service.compressConversation(original.id, original, "", 500, 0).isSuccess)
        assertEquals(listOf("first summary", "second summary"),
            f.repository.getConversationById(original.id)!!.currentMessages.map(UIMessage::toText))
        assertEquals(2, p.calls.size)
    }

    @Test
    fun `message translation persists an unselected branch and clear stays in memory`() = scenario { f, p ->
        val target = UIMessage.assistant(" Hello ")
        val other = UIMessage.assistant("Other").copy(translation = "keep")
        val original = conversation().copy(messageNodes = listOf(MessageNode(messages = listOf(target, other), selectIndex = 1)))
        f.load(original)
        p.stream = { call ->
            assertEquals("Hello -> zh_CN", call.messages.single().toText())
            flow { emit(response("你", delta = true)); emit(response("好", delta = true)) }
        }
        f.awaitLaunched { f.service.translateMessage(original.id, target, "zh-CN") }
        val saved = f.repository.getConversationById(original.id)!!
        assertEquals("你好", saved.messageNodes.single().messages.first().translation)
        assertEquals(other, saved.currentMessages.single())
        assertEquals(original.copy(messageNodes = saved.messageNodes), saved)
        f.service.clearTranslationField(original.id, target.id)
        assertNull(f.service.getConversationFlow(original.id).value.messageNodes.single().messages.first().translation)
        assertEquals(saved, f.repository.getConversationById(original.id))
        p.stream = { error("translation failed") }
        f.awaitLaunched { f.service.translateMessage(original.id, target, "zh-CN") }
        assertNull(f.service.getConversationFlow(original.id).value.messageNodes.single().messages.first().translation)
        assertEquals(saved, f.repository.getConversationById(original.id))
        assertEquals("translation failed", f.service.errors.value.single().error.message)
    }

    @Test
    fun `selection validates node before index and reads the current service state`() = scenario { f, _ ->
        val original = selectionConversation()
        val target = original.messageNodes[1]
        f.load(original)
        assertFailsWith<NotFoundException> { f.service.selectMessageNode(original.id, Uuid.random(), -1) }
        for (index in listOf(-1, target.messages.size, Int.MAX_VALUE)) {
            assertFailsWith<BadRequestException> { f.service.selectMessageNode(original.id, target.id, index) }
        }
        assertEquals(original, f.repository.getConversationById(original.id))
        f.service.selectMessageNode(original.id, target.id, 1)
        assertEquals(target.messages[1], f.repository.getConversationById(original.id)!!.currentMessages[1])
        f.service.updateConversationState(original.id) { it.copy(title = "latest") }
        f.service.selectMessageNode(original.id, target.id, 0)
        assertEquals("latest", f.repository.getConversationById(original.id)?.title)
        assertEquals(target.messages[0], f.repository.getConversationById(original.id)!!.currentMessages[1])
    }

    private fun scenario(block: suspend (ChatServiceTestFixture, RecordingChatProvider) -> Unit) = runTest(timeout = 10.seconds) {
        val provider = RecordingChatProvider()
        ChatServiceTestFixture(provider).use { fixture ->
            fixture.configure(settings())
            block(fixture, provider)
        }
    }

    private fun settings(model: Model = Model("test", "Test")) = Settings(
        providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
        chatModelId = model.id, titleModelId = model.id, suggestionModelId = model.id,
        compressModelId = model.id, translateModeId = model.id, enableSuggestion = true,
        titlePrompt = "{locale}\n{content}", suggestionPrompt = "{locale}\n{content}",
        compressPrompt = "{locale}\n{content}\n{target_tokens}\n{additional_context}",
        translatePrompt = "{source_text} -> {target_lang}",
    )

    private fun conversation() = Conversation.ofId(Uuid.random(), messages = listOf(
        UIMessage.user("oldmarker").toMessageNode(), UIMessage.assistant("recentmarker").toMessageNode(),
    )).copy(title = "Original", chatSuggestions = listOf("keep"), isPinned = true, customSystemPrompt = "keep prompt",
        createAt = kotlin.time.Instant.fromEpochMilliseconds(1000), updateAt = kotlin.time.Instant.fromEpochMilliseconds(2000))
}

internal class RecordingChatProvider : Provider<ProviderSetting.OpenAI> {
    data class Call(val setting: ProviderSetting.OpenAI, val messages: List<UIMessage>, val params: TextGenerationParams)
    val calls = java.util.concurrent.CopyOnWriteArrayList<Call>()
    var generate: suspend (Call) -> MessageChunk = { response("response") }
    var stream: suspend (Call) -> Flow<MessageChunk> = { flow { emit(response("response", delta = true)) } }
    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()
    override suspend fun generateText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): MessageChunk =
        generate(Call(providerSetting, messages, params).also(calls::add))
    override suspend fun streamText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): Flow<MessageChunk> =
        stream(Call(providerSetting, messages, params).also(calls::add))
    override suspend fun generateEmbedding(providerSetting: ProviderSetting.OpenAI, params: EmbeddingGenerationParams): EmbeddingGenerationResult = error("Unexpected embedding")
}

internal fun response(text: String?, delta: Boolean = false) = MessageChunk("test", "test", listOf(
    UIMessageChoice(0, if (delta) text?.let(UIMessage::assistant) else null,
        if (delta) null else text?.let(UIMessage::assistant), "stop"),
))

internal fun selectionConversation(): Conversation = Conversation.ofId(Uuid.random(), messages = listOf(
    UIMessage.user("Question").toMessageNode(),
    MessageNode(messages = listOf(UIMessage.assistant("originalmarker"), UIMessage.assistant("alternativemarker"))),
    UIMessage.user("Follow-up").toMessageNode(),
)).copy(title = "Branch selection", chatSuggestions = listOf("keep"), isPinned = true,
    createAt = kotlin.time.Instant.fromEpochMilliseconds(1000), updateAt = kotlin.time.Instant.fromEpochMilliseconds(2000))
