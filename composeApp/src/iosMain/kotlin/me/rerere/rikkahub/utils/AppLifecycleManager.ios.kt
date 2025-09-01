package me.rerere.rikkahub.utils

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

actual object AppLifecycleManager {
    private val observers = mutableSetOf<AppLifecycleObserver>()
    private val registeredObservers = mutableListOf<Any>()

    actual fun addObserver(observer: AppLifecycleObserver) {
        observers.add(observer)
        if (registeredObservers.isEmpty()) {
            registerNotifications()
        }
    }

    actual fun removeObserver(observer: AppLifecycleObserver) {
        observers.remove(observer)
        if (observers.isEmpty()) {
            unregisterNotifications()
        }
    }

    private fun registerNotifications() {
        val center = NSNotificationCenter.defaultCenter

        val foregroundObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) { _ ->
            observers.forEach { it.onAppForeground() }
        }

        val backgroundObserver = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) { _ ->
            observers.forEach { it.onAppBackground() }
        }

        registeredObservers.addAll(listOf(foregroundObserver, backgroundObserver))
    }

    private fun unregisterNotifications() {
        val center = NSNotificationCenter.defaultCenter
        registeredObservers.forEach { observer ->
            center.removeObserver(observer)
        }
        registeredObservers.clear()
    }
}
