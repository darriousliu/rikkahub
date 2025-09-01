package me.rerere.rikkahub.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

internal actual suspend fun sendGenerationDoneNotification(
    conversation: Conversation,
    context: PlatformContext,
    conversationId: Uuid
) {
    val notification =
        NotificationCompat.Builder(context, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_chat_done_title))
            .setContentText(conversation.currentMessages.lastOrNull()?.toText()?.take(50) ?: "")
            .setSmallIcon(R.drawable.small_icon)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(getPendingIntent(context, conversationId))

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    NotificationManagerCompat.from(context).notify(1, notification.build())
}

private fun getPendingIntent(
    context: PlatformContext,
    conversationId: Uuid
): PendingIntent {
    val intent = Intent(context, RouteActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("conversationId", conversationId.toString())
    }
    return PendingIntent.getActivity(
        context,
        conversationId.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

