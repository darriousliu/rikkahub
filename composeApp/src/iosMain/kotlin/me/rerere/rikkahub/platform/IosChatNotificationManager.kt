@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.rikkahub.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.darwin.NSObjectProtocol

public actual class ChatNotificationManager actual constructor() {
    private val foreground = MutableStateFlow(false)
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val lifecycleObservers = mutableListOf<NSObjectProtocol>()
    private var coordinator: ChatNotificationCoordinator? = null

    public actual fun start(
        scope: CoroutineScope,
        eventBus: AppEventBus,
        settingsStore: SettingsStore,
        presenter: ChatNotificationPresenter,
    ) {
        close()
        foreground.value =
            UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
        lifecycleObservers += notificationCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { foreground.value = true },
        )
        lifecycleObservers += notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { foreground.value = false },
        )
        coordinator = ChatNotificationCoordinator(
            scope = scope,
            eventBus = eventBus,
            settingsStore = settingsStore,
            foreground = foreground,
            presenter = presenter,
            monotonicMillis = { (NSProcessInfo.processInfo.systemUptime * 1_000.0).toLong() },
        )
    }

    public actual fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
    }

    public actual fun close() {
        lifecycleObservers.forEach(notificationCenter::removeObserver)
        lifecycleObservers.clear()
        coordinator?.close()
        coordinator = null
    }
}
