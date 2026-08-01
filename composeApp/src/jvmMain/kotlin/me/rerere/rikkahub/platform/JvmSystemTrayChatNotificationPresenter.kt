package me.rerere.rikkahub.platform

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import kotlin.uuid.Uuid

public class JvmSystemTrayChatNotificationPresenter : ChatNotificationPresenter {
    private var trayIcon: TrayIcon? = null

    override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {
        val (status, body) = notification.phase.toDisplayContent()
        showMessage(
            title = "${notification.senderName} · $status",
            body = body,
            messageType = TrayIcon.MessageType.NONE,
        )
    }

    override fun showGenerationCompleted(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String,
    ) {
        showMessage(
            title = senderName,
            body = contentPreview,
            messageType = TrayIcon.MessageType.INFO,
        )
    }

    override fun cancelLiveUpdate(conversationId: Uuid) = Unit

    override fun close() {
        trayIcon?.let { icon -> runCatching { SystemTray.getSystemTray().remove(icon) } }
        trayIcon = null
    }

    private fun showMessage(
        title: String,
        body: String,
        messageType: TrayIcon.MessageType,
    ) {
        val icon = getOrCreateTrayIcon() ?: return
        runCatching { icon.displayMessage(title, body, messageType) }
    }

    private fun getOrCreateTrayIcon(): TrayIcon? {
        trayIcon?.let { return it }
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return null
        return runCatching {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
                createGraphics().run {
                    color = Color(0x67, 0x50, 0xA4)
                    fillOval(1, 1, 14, 14)
                    dispose()
                }
            }
            TrayIcon(image, "RikkaHub").also { icon ->
                icon.isImageAutoSize = true
                SystemTray.getSystemTray().add(icon)
                trayIcon = icon
            }
        }.getOrNull()
    }

    private fun ChatNotificationPhase.toDisplayContent(): Pair<String, String> = when (this) {
        is ChatNotificationPhase.Tool -> "Running tool: $toolName" to inputPreview
        is ChatNotificationPhase.Thinking -> "Thinking…" to preview
        is ChatNotificationPhase.Writing -> "Writing response…" to preview
        ChatNotificationPhase.Starting -> "Generating response…" to ""
    }
}
