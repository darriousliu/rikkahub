@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.rikkahub.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

internal actual fun observeChatNotificationForeground(onChanged: (Boolean) -> Unit): () -> Unit {
    val center = NSNotificationCenter.defaultCenter
    onChanged(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
    val active = center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
        usingBlock = { onChanged(true) },
    )
    val background = center.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
        usingBlock = { onChanged(false) },
    )
    return {
        center.removeObserver(active)
        center.removeObserver(background)
    }
}

internal actual fun notificationTimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000.0).toLong()
