package me.rerere.rikkahub.utils

import me.rerere.common.PlatformContext

/**
 * 通知构建器的配置 DSL
 */
class NotificationConfig {
    var title: String = ""
    var content: String = ""
    var subText: String? = null
    var autoCancel: Boolean = false
    var ongoing: Boolean = false
    var onlyAlertOnce: Boolean = false
    var useBigTextStyle: Boolean = false

    // 默认通知效果
    var useDefaults: Boolean = false

    /**
     * 平台专属附加数据，通过 platformExtras { } DSL 配置
     * Android: AndroidNotificationExtras
     * iOS: IOSNotificationExtras
     */
    var platformExtras: Any? = null
}

expect object NotificationUtil {

    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: PlatformContext): Boolean

    /**
     * 使用 DSL 风格创建并发送通知
     *
     * @param context 上下文
     * @param channelId 通知渠道 ID
     * @param notificationId 通知 ID
     * @param config 通知配置 lambda
     * @return 是否成功发送
     */
    fun notify(
        context: PlatformContext,
        channelId: String,
        notificationId: Int,
        config: NotificationConfig.() -> Unit
    ): Boolean

    /**
     * 取消通知
     */
    fun cancel(context: PlatformContext, notificationId: Int)

    /**
     * 取消所有通知
     */
    fun cancelAll(context: PlatformContext)
}

/**
 * Context 扩展函数，简化通知发送
 */
fun PlatformContext.sendNotification(
    channelId: String,
    notificationId: Int,
    config: NotificationConfig.() -> Unit
): Boolean = NotificationUtil.notify(this, channelId, notificationId, config)

/**
 * Context 扩展函数，取消通知
 */
fun PlatformContext.cancelNotification(notificationId: Int) {
    NotificationUtil.cancel(this, notificationId)
}
