package me.rerere.rikkahub.platform

internal actual fun observeChatNotificationForeground(onChanged: (Boolean) -> Unit): () -> Unit = {}

internal actual fun notificationTimeMillis(): Long = System.nanoTime() / 1_000_000L
