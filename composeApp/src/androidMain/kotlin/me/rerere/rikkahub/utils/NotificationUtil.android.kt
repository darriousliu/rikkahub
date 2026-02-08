package me.rerere.rikkahub.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.common.PlatformContext
import me.rerere.composeapp.R

/**
 * Android 专属扩展配置
 */
class AndroidNotificationExtras {
    var smallIcon: Int = R.drawable.small_icon
    var category: String? = null
    var visibility: Int = NotificationCompat.VISIBILITY_PRIVATE
    var contentIntent: PendingIntent? = null

    // Live Update 相关
    var requestPromotedOngoing: Boolean = false
    var shortCriticalText: String? = null
}

fun NotificationConfig.android(block: AndroidNotificationExtras.() -> Unit) {
    val extras = (platformExtras as? AndroidNotificationExtras) ?: AndroidNotificationExtras()
    extras.apply(block)
    platformExtras = extras
}

actual object NotificationUtil {
    actual fun hasNotificationPermission(context: PlatformContext): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    actual fun notify(
        context: PlatformContext,
        channelId: String,
        notificationId: Int,
        config: NotificationConfig.() -> Unit
    ): Boolean {
        if (!hasNotificationPermission(context)) {
            return false
        }

        val notificationConfig = NotificationConfig().apply(config)
        val androidExtras = (notificationConfig.platformExtras as? AndroidNotificationExtras)
            ?: AndroidNotificationExtras()
        val notification = buildNotification(context, channelId, notificationConfig, androidExtras)

        NotificationManagerCompat.from(context).notify(notificationId, notification.build())
        return true
    }


    /**
     * 构建通知
     */
    fun buildNotification(
        context: PlatformContext,
        channelId: String,
        config: NotificationConfig,
        extras: AndroidNotificationExtras
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId).apply {
            setContentTitle(config.title)
            setContentText(config.content)
            setSmallIcon(extras.smallIcon)
            setAutoCancel(config.autoCancel)
            setOngoing(config.ongoing)
            setOnlyAlertOnce(config.onlyAlertOnce)
            setVisibility(extras.visibility)

            config.subText?.let { setSubText(it) }
            extras.category?.let { setCategory(it) }
            extras.contentIntent?.let { setContentIntent(it) }

            if (config.useBigTextStyle) {
                setStyle(NotificationCompat.BigTextStyle().bigText(config.content))
            }

            if (config.useDefaults) {
                setDefaults(NotificationCompat.DEFAULT_ALL)
            }

            // Android 15+ Live Update 支持
            if (extras.requestPromotedOngoing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                setRequestPromotedOngoing(true)
            }

            // Android 16+ 状态栏 chip 文本
            if (extras.shortCriticalText != null && Build.VERSION.SDK_INT >= 36) {
                setShortCriticalText(extras.shortCriticalText!!)
            }
        }
    }

    actual fun cancel(context: PlatformContext, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    actual fun cancelAll(context: PlatformContext) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
