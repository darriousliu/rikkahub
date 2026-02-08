package me.rerere.rikkahub.utils

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.PlatformContext
import platform.Foundation.NSNumber
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS 专属扩展配置
 */
class IOSNotificationExtras {
    var badge: NSNumber? = null
    var sound: Boolean = true
    var categoryIdentifier: String? = null

    /** 延迟触发秒数，null 则立即触发（iOS 要求 timeInterval > 0，最小设为 0.1） */
    var triggerDelaySeconds: Double? = null
    // ---- 新增 ----
    var subtitle: String? = null
    var userInfo: Map<String, Any> = emptyMap()
    /** 标记为更新型通知（替换已有同 ID 通知，不重复提醒） */
    var isUpdate: Boolean = false
}

fun NotificationConfig.ios(block: IOSNotificationExtras.() -> Unit) {
    val extras = (platformExtras as? IOSNotificationExtras) ?: IOSNotificationExtras()
    extras.apply(block)
    platformExtras = extras
}

actual object NotificationUtil {
    private val center: UNUserNotificationCenter
        get() = UNUserNotificationCenter.currentNotificationCenter()

    actual fun hasNotificationPermission(context: PlatformContext): Boolean {
        // iOS 权限检查是异步的，这里用 runBlocking 桥接
        return runBlocking {
            suspendCancellableCoroutine { cont ->
                center.getNotificationSettingsWithCompletionHandler { settings ->
                    cont.resume(
                        settings?.authorizationStatus == UNAuthorizationStatusAuthorized
                    )
                }
            }
        }
    }

    actual fun notify(
        context: PlatformContext,
        channelId: String,   // iOS 上映射为 threadIdentifier（通知分组）
        notificationId: Int,
        config: NotificationConfig.() -> Unit
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
    val cfg = NotificationConfig().apply(config)
    val iosExtras = (cfg.platformExtras as? IOSNotificationExtras)
        ?: IOSNotificationExtras()
    val content = UNMutableNotificationContent().apply {
        setTitle(cfg.title)
        setBody(cfg.content)
        // 优先用 iosExtras.subtitle，否则回退到 cfg.subText
        val sub = iosExtras.subtitle ?: cfg.subText
        sub?.let { setSubtitle(it) }
        setThreadIdentifier(channelId)
        iosExtras.categoryIdentifier?.let { setCategoryIdentifier(it) }
        iosExtras.badge?.let { setBadge(it) }
        if (iosExtras.sound) {
            setSound(UNNotificationSound.defaultSound())
        }
        // userInfo 传递自定义数据
        if (iosExtras.userInfo.isNotEmpty()) {
            setUserInfo(iosExtras.userInfo.mapValues { it.value })
        }
        // 更新通知时抑制重复提醒（iOS 15+）
        if (iosExtras.isUpdate) {
            // 同一 identifier 的通知会自动替换
            // 通过不设置 sound 来避免重复提醒（已在上面处理）
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive)
        }
    }
    val delaySeconds = iosExtras.triggerDelaySeconds ?: 0.1
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
        timeInterval = delaySeconds,
        repeats = false
    )
    val requestId = "notification_$notificationId"
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = requestId,
        content = content,
        trigger = trigger
    )
    center.addNotificationRequest(request) { error ->
        if (error != null) {
            println("NotificationUtil iOS error: ${error.localizedDescription}")
        }
    }
    return true
    }

    actual fun cancel(context: PlatformContext, notificationId: Int) {
        val requestId = "notification_$notificationId"
        center.removePendingNotificationRequestsWithIdentifiers(listOf(requestId))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(requestId))
    }

    actual fun cancelAll(context: PlatformContext) {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}
