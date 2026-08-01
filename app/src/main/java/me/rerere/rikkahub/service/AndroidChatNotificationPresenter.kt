package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CONVERSATION_ID_EXTRA
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.platform.ChatLiveUpdateNotification
import me.rerere.rikkahub.platform.ChatNotificationPhase
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.rikkahub.utils.sendNotification
import kotlin.uuid.Uuid

class AndroidChatNotificationPresenter(
    private val context: Application,
) : ChatNotificationPresenter {
    override fun showGenerationCompleted(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String,
    ) {
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1,
        ) {
            title = senderName
            content = contentPreview
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {
        val (chipText, statusText, contentText) = notification.phase.toDisplayContent()
        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(notification.conversationId),
        ) {
            title = notification.senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, notification.conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    override fun cancelLiveUpdate(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    private fun ChatNotificationPhase.toDisplayContent(): Triple<String, String, String> = when (this) {
        is ChatNotificationPhase.Tool -> Triple(
            context.getString(R.string.notification_live_update_chip_tool),
            context.getString(R.string.notification_live_update_tool, toolName),
            inputPreview,
        )

        is ChatNotificationPhase.Thinking -> Triple(
            context.getString(R.string.notification_live_update_chip_thinking),
            context.getString(R.string.notification_live_update_thinking),
            preview,
        )

        is ChatNotificationPhase.Writing -> Triple(
            context.getString(R.string.notification_live_update_chip_writing),
            context.getString(R.string.notification_live_update_writing),
            preview,
        )

        ChatNotificationPhase.Starting -> Triple(
            context.getString(R.string.notification_live_update_chip_writing),
            context.getString(R.string.notification_live_update_title),
            "",
        )
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int = conversationId.hashCode() + 10_000

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CONVERSATION_ID_EXTRA, conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
