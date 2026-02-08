package me.rerere.rikkahub.service

import me.rerere.common.PlatformContext
import me.rerere.rikkahub.utils.NotificationConfig
import me.rerere.rikkahub.utils.ios
import kotlin.uuid.Uuid

actual fun NotificationConfig.platformMessageCompleteConfigure(
    context: PlatformContext,
    conversationId: Uuid
) {
    ios {
        // 通过 userInfo 传递 conversationId，供 UNUserNotificationCenterDelegate 跳转
        userInfo = mapOf(
            "conversationId" to conversationId.toString(),
            "action" to "open_conversation"
        )
        categoryIdentifier = "MESSAGE"
        sound = true
    }
}

actual fun NotificationConfig.platformMessageUpdateConfigure(
    context: PlatformContext,
    conversationId: Uuid,
    chipText: String
) {
    ios {
        userInfo = mapOf(
            "conversationId" to conversationId.toString(),
            "action" to "open_conversation"
        )
        categoryIdentifier = "MESSAGE_PROGRESS"
        // iOS 没有 Android 状态栏 chip，将 chipText 映射到 subtitle
        subtitle = chipText
        // 更新时不重复响铃
        sound = false
        // 标记为进行中的更新，App 侧可据此决定是否替换通知
        isUpdate = true
    }
}
