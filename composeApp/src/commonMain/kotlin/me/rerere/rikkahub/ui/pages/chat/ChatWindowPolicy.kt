package me.rerere.rikkahub.ui.pages.chat

enum class ChatDrawerPresentation {
    Modal,
    Permanent,
}

fun selectChatDrawerPresentation(
    widthDp: Float,
    heightDp: Float,
): ChatDrawerPresentation = if (widthDp >= 1100f && widthDp > heightDp) {
    ChatDrawerPresentation.Permanent
} else {
    ChatDrawerPresentation.Modal
}
