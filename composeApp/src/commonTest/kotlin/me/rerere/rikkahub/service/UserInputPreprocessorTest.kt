package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UserInputPreprocessorTest {
    @Test
    fun `only enabled nonvisual rules affecting users are applied`() {
        val assistant = Assistant(regexes = listOf(
            rule("cat", "disabled").copy(enabled = false),
            rule("cat", "visual").copy(visualOnly = true),
            rule("cat", "assistant").copy(affectingScope = setOf(AssistantAffectScope.ASSISTANT)),
            rule("cat", "unscoped").copy(affectingScope = emptySet()),
            rule("cat", "dog"),
            rule("dog", "fox").copy(
                affectingScope = setOf(AssistantAffectScope.USER, AssistantAffectScope.ASSISTANT),
            ),
        ))

        assertEquals(
            listOf(UIMessagePart.Text("fox")),
            preprocessUserInputParts(listOf(UIMessagePart.Text("cat")), assistant),
        )
    }

    @Test
    fun `rules run in order with capture groups across all text parts`() {
        val assistant = Assistant(regexes = listOf(
            rule("cat-(\\d+)", "${'$'}1 cat"),
            rule("cat", "猫"),
        ))
        val parts = listOf(UIMessagePart.Text("cat-12\ncat-7"), UIMessagePart.Text("cat-3"))

        assertEquals(
            listOf(UIMessagePart.Text("12 猫\n7 猫"), UIMessagePart.Text("3 猫")),
            preprocessUserInputParts(parts, assistant),
        )
        assertEquals("cat-12\ncat-7", parts[0].text)
    }

    @Test
    fun `metadata order and nontext references including nested tool text stay intact`() {
        val metadata = JsonObject(mapOf("source" to JsonPrimitive("keep")))
        val text = UIMessagePart.Text("cat", metadata)
        val nonText = listOf(
            UIMessagePart.Image("file:///cat.png"),
            UIMessagePart.Document("file:///cat.txt", "cat.txt"),
            UIMessagePart.Video("file:///cat.mp4"),
            UIMessagePart.Audio("file:///cat.mp3"),
            UIMessagePart.Reasoning("cat"),
            UIMessagePart.Tool(
                toolCallId = "cat",
                toolName = "cat",
                input = "cat",
                output = listOf(UIMessagePart.Text("cat")),
                metadata = metadata,
            ),
        )
        val parts = listOf(nonText[0], text) + nonText.drop(1) + UIMessagePart.Text("cat tail")

        val processed = preprocessUserInputParts(parts, Assistant(regexes = listOf(rule("cat", "dog"))))

        assertEquals(
            listOf(nonText[0], text.copy(text = "dog")) + nonText.drop(1) + UIMessagePart.Text("dog tail"),
            processed,
        )
        parts.forEachIndexed { index, part ->
            if (part !is UIMessagePart.Text) assertSame(part, processed[index])
        }
        assertSame(metadata, processed[1].metadata)
        assertEquals("cat", text.text)
        assertEquals(JsonObject(mapOf("source" to JsonPrimitive("keep"))), metadata)
    }

    @Test
    fun `invalid patterns and replacement groups keep prior text and allow later rules`() {
        val assistant = Assistant(regexes = listOf(
            rule("(", "invalid"),
            rule("cat", "dog"),
            rule("(dog)", "${'$'}2"),
            rule("dog", "fox"),
        ))

        assertEquals(
            listOf(UIMessagePart.Text("fox-1")),
            preprocessUserInputParts(listOf(UIMessagePart.Text("cat-1")), assistant),
        )
    }

    @Test
    fun `empty input and text without matching rules remain unchanged`() {
        val parts = listOf(UIMessagePart.Text("  unchanged\n"), UIMessagePart.Text(""))
        val assistants = listOf(Assistant(), Assistant(regexes = listOf(rule("absent", "replacement"))))

        assistants.forEach { assistant ->
            assertEquals(parts, preprocessUserInputParts(parts, assistant))
            assertTrue(preprocessUserInputParts(emptyList(), assistant).isEmpty())
        }
    }

    @Test
    fun `replacements yielding empty or whitespace text retain the text parts`() {
        val metadata = JsonObject(mapOf("keep" to JsonPrimitive(true)))
        val parts = listOf(UIMessagePart.Text("erase", metadata), UIMessagePart.Text("  cat  "))
        val assistant = Assistant(regexes = listOf(rule("erase", ""), rule("cat", " ")))

        assertEquals(
            listOf(UIMessagePart.Text("", metadata), UIMessagePart.Text("     ")),
            preprocessUserInputParts(parts, assistant),
        )
    }

    private fun rule(pattern: String, replacement: String) = AssistantRegex(
        id = Uuid.random(),
        findRegex = pattern,
        replaceString = replacement,
        affectingScope = setOf(AssistantAffectScope.USER),
    )
}
