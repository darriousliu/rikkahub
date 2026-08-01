@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.rikkahub.platform

import platform.Foundation.NSBundle
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.uuid.Uuid

public class IosUserNotificationPresenter(
    private val notificationCenter: UNUserNotificationCenter? = defaultUserNotificationCenter(),
) : ChatNotificationPresenter {
    override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {
        val (subtitle, body) = notification.phase.toDisplayContent()
        deliver(
            identifier = notification.conversationId.liveUpdateIdentifier(),
            conversationId = notification.conversationId,
            title = notification.senderName,
            subtitle = subtitle,
            body = body,
        )
    }

    override fun showGenerationCompleted(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String,
    ) {
        deliver(
            identifier = "chat-completed-$conversationId",
            conversationId = conversationId,
            title = senderName,
            subtitle = "Response completed",
            body = contentPreview,
        )
    }

    override fun cancelLiveUpdate(conversationId: Uuid) {
        val notificationCenter = notificationCenter ?: return
        val identifiers = listOf(conversationId.liveUpdateIdentifier())
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    private fun deliver(
        identifier: String,
        conversationId: Uuid,
        title: String,
        subtitle: String,
        body: String,
    ) {
        val notificationCenter = notificationCenter ?: return
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val authorized = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized,
                UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral,
                -> true

                else -> false
            }
            if (!authorized) return@getNotificationSettingsWithCompletionHandler

            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setSubtitle(subtitle)
                setBody(body)
                setThreadIdentifier("chat")
                setUserInfo(mapOf("conversationId" to conversationId.toString()))
            }
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = identifier,
                content = content,
                trigger = null,
            )
            notificationCenter.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    private fun ChatNotificationPhase.toDisplayContent(): Pair<String, String> = when (this) {
        is ChatNotificationPhase.Tool -> "Running tool: $toolName" to inputPreview
        is ChatNotificationPhase.Thinking -> "Thinking…" to preview
        is ChatNotificationPhase.Writing -> "Writing response…" to preview
        ChatNotificationPhase.Starting -> "Generating response…" to ""
    }

    private fun Uuid.liveUpdateIdentifier(): String = "chat-live-$this"
}

private fun defaultUserNotificationCenter(): UNUserNotificationCenter? =
    if (NSBundle.mainBundle.bundleIdentifier.isNullOrBlank()) {
        null
    } else {
        UNUserNotificationCenter.currentNotificationCenter()
    }
