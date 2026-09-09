package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes

fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
    return parts.map { part ->
        when (part) {
            is UIMessagePart.Text -> {
                part.copy(
                    text = part.text.replaceRegexes(
                        assistant = assistant,
                        scope = AssistantAffectScope.USER,
                        visual = false
                    )
                )
            }

            else -> part
        }
    }
}
