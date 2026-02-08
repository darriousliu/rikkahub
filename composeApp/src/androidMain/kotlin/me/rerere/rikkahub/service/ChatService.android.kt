package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.utils.NotificationConfig
import me.rerere.rikkahub.utils.android
import kotlin.uuid.Uuid

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

actual fun NotificationConfig.platformMessageCompleteConfigure(
    context: PlatformContext,
    conversationId: Uuid
) {
    android {
        category = NotificationCompat.CATEGORY_MESSAGE
        contentIntent = getPendingIntent(context, conversationId)
    }
}

actual fun NotificationConfig.platformMessageUpdateConfigure(
    context: PlatformContext,
    conversationId: Uuid,
    chipText: String
) {
    android {
        category = NotificationCompat.CATEGORY_PROGRESS
        contentIntent = getPendingIntent(context, conversationId)
        requestPromotedOngoing = true
        shortCriticalText = chipText
    }
}
