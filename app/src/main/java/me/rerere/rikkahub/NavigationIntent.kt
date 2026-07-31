package me.rerere.rikkahub

internal const val CONVERSATION_ID_EXTRA = "conversationId"

internal fun conversationScreen(conversationId: String?): Screen.Chat? {
    return conversationId?.let { Screen.Chat(id = it) }
}
