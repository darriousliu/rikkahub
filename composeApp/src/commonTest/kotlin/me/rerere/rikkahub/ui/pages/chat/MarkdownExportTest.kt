package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MarkdownExportTest {
    @Test
    fun exportsOnlySuppliedMessagesInOrderWithOriginalMarkdownFormatting() {
        val omitted = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("not selected")))
        val user = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("中文 **question**")))
        val assistant = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("answer\nnext line")))
        val conversation = Conversation(
            assistantId = Uuid.random(),
            title = "Export title",
            messageNodes = listOf(MessageNode(messages = listOf(omitted))),
        )

        val markdown = exportToMarkdown(conversation, listOf(user, assistant))

        assertTrue(markdown.startsWith("# Export title\n\n*Exported on "))
        assertEquals(
            "**User**:\n\n中文 **question**\n\n---\n**Assistant**:\n\nanswer\nnext line\n\n---\n",
            markdown.substringAfter("*\n\n"),
        )
        assertFalse(markdown.contains("not selected"))
    }

    @Test
    fun preservesReasoningToolOutputAndMediaBehaviorFromOriginalExporter() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning("first\n\nsecond"),
                UIMessagePart.Image("data:image/png;base64,YQ=="),
                UIMessagePart.Image("unsupported-image"),
                UIMessagePart.Document("ignored", "ignored.txt"),
                UIMessagePart.Tool(
                    toolCallId = "call-1",
                    toolName = "lookup",
                    input = "{\"q\":\"中文\"}",
                    output = listOf(
                        UIMessagePart.Text("found"),
                        UIMessagePart.Reasoning("one\n\ntwo"),
                        UIMessagePart.Image("https://example.com/image.png"),
                        UIMessagePart.Document("file:///note.txt", "note.txt"),
                        UIMessagePart.Video("https://example.com/video.mp4"),
                        UIMessagePart.Audio("https://example.com/audio.mp3"),
                    ),
                ),
            ),
        )

        val body = exportToMarkdown(conversation(), listOf(message)).substringAfter("*\n\n")

        assertEquals(
            "**Assistant**:\n\n> first> second\n\n" +
                "![Image](data:image/png;base64,YQ==)\n![Image](null)\n" +
                "**Tool**: `lookup`\n- Call ID: `call-1`\nInput:\n```json\n" +
                "{\n    \"q\": \"中文\"\n}\n```\nOutput:\n```text\nfound\n```\n" +
                "> one\n> two\n![Tool Image](https://example.com/image.png)\n" +
                "[Document: note.txt](file:///note.txt)\n" +
                "[Video](https://example.com/video.mp4)\n" +
                "[Audio](https://example.com/audio.mp3)\n\n\n---\n",
            body,
        )
    }

    @Test
    fun keepsEmptySelectionAndToolWithoutOutputBehavior() {
        assertEquals("", exportToMarkdown(conversation(), emptyList()).substringAfter("*\n\n"))
        val message = UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Tool(toolCallId = "", toolName = "empty", input = "invalid")),
        )
        assertEquals(
            "**Assistant**:\n\n**Tool**: `empty`\nInput:\n```json\n\"invalid\"\n```\n\n\n---\n",
            exportToMarkdown(conversation(), listOf(message)).substringAfter("*\n\n"),
        )
    }

    private fun conversation() = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
}
