package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenerationHandlerToolsTest {
    @Test
    fun `approval suspends execution then resumes once before the next model request`() = runTest {
        val provider = RecordingChatProvider()
        ChatServiceTestFixture(provider).use { f ->
            val model = Model("tools", "Tools")
            val settings = Settings(providers = listOf(ProviderSetting.OpenAI(models = listOf(model))))
            val assistant = Assistant(streamOutput = false, enableMemory = false)
            var executions = 0
            val tool = Tool("echo", "Echo", needsApproval = { true }) {
                executions++
                listOf(UIMessagePart.Text("tool result"))
            }
            provider.generate = {
                response("").let { chunk -> chunk.copy(choices = chunk.choices.map { choice ->
                    choice.copy(message = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Tool("call", "echo", "{}"))))
                }) }
            }
            val waiting = assertIs<GenerationChunk.Messages>(f.generationHandler.generateText(
                settings, model, listOf(UIMessage.user("question")), assistant = assistant, tools = listOf(tool),
            ).last()).messages
            assertIs<ToolApprovalState.Pending>(waiting.last().getTools().single().approvalState)
            assertEquals(0, executions)
            assertEquals(1, provider.calls.size)
            val approved = waiting.dropLast(1) + waiting.last().copy(parts = waiting.last().parts.map {
                if (it is UIMessagePart.Tool) it.copy(approvalState = ToolApprovalState.Approved) else it
            })
            provider.generate = { call ->
                assertTrue(call.messages.any { message -> message.getTools().any { it.output == listOf(UIMessagePart.Text("tool result")) } })
                response("completed")
            }
            val result = assertIs<GenerationChunk.Messages>(f.generationHandler.generateText(
                settings, model, approved, assistant = assistant, tools = listOf(tool),
            ).last()).messages
            assertEquals("completed", result.last().parts.filterIsInstance<UIMessagePart.Text>().last().text)
            assertEquals(1, executions)
            assertEquals(2, provider.calls.size)
        }
    }

    @Test
    fun `denied and answered tools resume without executing the tool implementation`() = runTest {
        ChatServiceTestFixture(RecordingChatProvider()).use { f ->
            val model = Model("tools", "Tools")
            val settings = Settings(providers = listOf(ProviderSetting.OpenAI(models = listOf(model))))
            for (state in listOf(ToolApprovalState.Denied("No"), ToolApprovalState.Answered("Answer"))) {
                val result = assertIs<GenerationChunk.Messages>(f.generationHandler.generateText(
                    settings, model, listOf(UIMessage.user("question"), UIMessage.assistant("").copy(parts = listOf(
                        UIMessagePart.Tool("call", "echo", "{}", approvalState = state),
                    ))), assistant = Assistant(streamOutput = false, enableMemory = false),
                    tools = listOf(Tool("echo", "Echo") { error("Must not execute") }),
                ).last()).messages
                val output = result.flatMap { it.getTools() }.single().output.single() as UIMessagePart.Text
                assertTrue(if (state is ToolApprovalState.Answered) output.text == "Answer" else "Reason: No" in output.text)
            }
        }
    }

    @Test
    fun `tool cancellation propagates instead of becoming a tool error result`() = runTest {
        ChatServiceTestFixture(RecordingChatProvider()).use { f ->
            val model = Model("tools", "Tools")
            assertFailsWith<CancellationException> {
                f.generationHandler.generateText(
                    Settings(providers = listOf(ProviderSetting.OpenAI(models = listOf(model)))), model,
                    listOf(UIMessage.user("question"), UIMessage.assistant("").copy(parts = listOf(
                        UIMessagePart.Tool("call", "echo", "{}", approvalState = ToolApprovalState.Approved),
                    ))), assistant = Assistant(streamOutput = false, enableMemory = false),
                    tools = listOf(Tool("echo", "Echo") { throw CancellationException("stop") }),
                ).last()
            }
        }
    }
}
