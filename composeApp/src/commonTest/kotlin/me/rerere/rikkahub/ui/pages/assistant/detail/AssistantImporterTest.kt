package me.rerere.rikkahub.ui.pages.assistant.detail

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_importer_missing_data_field
import me.rerere.rikkahub.generated.resources.assistant_importer_missing_name_field
import me.rerere.rikkahub.generated.resources.assistant_importer_missing_spec_field
import me.rerere.rikkahub.generated.resources.assistant_importer_unsupported_spec
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssistantImporterTest {
    @Test
    fun v2AndV3KeepOriginalPromptFieldsGreetingAndBackground() = runTest {
        for (spec in listOf("chara_card_v2", "chara_card_v3")) {
            val assistant = parseAssistantFromJson(
                Json.parseToJsonElement(
                    """{"spec":"$spec","data":{"name":"兔子","first_mes":"Hello","system_prompt":"System","description":"Description","personality":"Personality","scenario":"Scenario"}}"""
                ).jsonObject,
                "file:///card.png",
            )
            assertEquals("兔子", assistant.name)
            assertEquals("file:///card.png", assistant.background)
            assertEquals(
                "You are roleplaying as 兔子.\n\nSystem\n\n## Description of the character\nDescription\n\n" +
                    "## Personality of the character\nPersonality\n\n## Scenario\nScenario",
                assistant.systemPrompt,
            )
            assertEquals(MessageRole.ASSISTANT, assistant.presetMessages.single().role)
            assertEquals("Hello", assistant.presetMessages.single().parts.filterIsInstance<UIMessagePart.Text>().single().text)
        }
    }

    @Test
    fun absentOptionalFieldsKeepEmptyDefaultsAndAnExplicitEmptyGreeting() = runTest {
        val assistant = parseAssistantFromJson(
            Json.parseToJsonElement("""{"spec":"chara_card_v2","data":{"name":"Card","system_prompt":"  "}}""").jsonObject,
            null,
        )
        assertTrue(assistant.presetMessages.isEmpty())
        assertEquals(null, assistant.background)
        assertEquals(
            "You are roleplaying as Card.\n\n## Description of the character\nEmpty\n\n" +
                "## Personality of the character\nEmpty\n\n## Scenario\nEmpty",
            assistant.systemPrompt,
        )
        val emptyGreeting = parseAssistantFromJson(
            Json.parseToJsonElement("""{"spec":"chara_card_v3","data":{"name":"Card","first_mes":""}}""").jsonObject,
            null,
        )
        assertEquals(1, emptyGreeting.presetMessages.size)
    }

    @Test
    fun invalidCardsUseOriginalResourceErrorsAndExceptionType() = runTest {
        val cases = listOf(
            "{}" to getString(Res.string.assistant_importer_missing_spec_field),
            """{"spec":"unknown"}""" to getString(Res.string.assistant_importer_unsupported_spec, "unknown"),
            """{"spec":"chara_card_v2"}""" to getString(Res.string.assistant_importer_missing_data_field),
            """{"spec":"chara_card_v3","data":{}}""" to getString(Res.string.assistant_importer_missing_name_field),
        )
        for ((json, message) in cases) {
            val error = assertFailsWith<IllegalStateException> {
                parseAssistantFromJson(Json.parseToJsonElement(json).jsonObject, null)
            }
            assertEquals(message, error.message)
        }
    }
}
