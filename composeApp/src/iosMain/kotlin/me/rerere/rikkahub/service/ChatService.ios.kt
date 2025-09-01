package me.rerere.rikkahub.service

import co.touchlab.kermit.Logger
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.model.Conversation
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import rikkahub.composeapp.generated.resources.*
import kotlin.uuid.Uuid

internal actual suspend fun sendGenerationDoneNotification(
    conversation: Conversation,
    context: PlatformContext,
    conversationId: Uuid
) {
    Logger.i("ChatService") { "Sending generation done notification" }
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val title = getString(Res.string.notification_chat_done_title)

    // 检查通知权限
    center.getNotificationSettingsWithCompletionHandler { settings ->
        if (settings?.authorizationStatus != UNAuthorizationStatusAuthorized) {
            return@getNotificationSettingsWithCompletionHandler
        }

        // 创建通知内容
        val content = UNMutableNotificationContent().apply {
            setTitle(title) // 对应 R.string.notification_chat_done_title
            setBody(conversation.currentMessages.lastOrNull()?.toText()?.take(50) ?: "")
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier("CHAT_MESSAGE")

            // 添加用户信息，用于点击处理
            setUserInfo(
                mapOf(
                    "conversationId" to conversationId.toString()
                )
            )
        }

        // 创建触发器（立即显示）
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = 0.1,
            repeats = false
        )

        // 创建请求
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "chat_done_${conversationId}",
            content = content,
            trigger = trigger
        )

        // 发送通知
        center.addNotificationRequest(request) { error ->
            error?.let {
                println("Failed to send notification: ${it.localizedDescription}")
            }
        }
    }
}
