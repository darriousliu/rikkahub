package me.rerere.rikkahub.ui.pages.chat

import kotlinx.datetime.LocalDate
import me.rerere.rikkahub.data.model.Conversation

sealed interface ConversationDateLabel {
    data object Today : ConversationDateLabel
    data object Yesterday : ConversationDateLabel
    data class Formatted(val value: String) : ConversationDateLabel
}

sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: ConversationDateLabel,
    ) : ConversationListItem()

    data object PinnedHeader : ConversationListItem()

    data class Item(
        val conversation: Conversation,
    ) : ConversationListItem()
}
