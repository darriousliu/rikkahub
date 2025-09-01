package me.rerere.rikkahub.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

actual object AppLifecycleManager {
    private val observers = mutableSetOf<AppLifecycleObserver>()
    private var isRegistered = false

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                observers.forEach { it.onAppForeground() }
            }

            Lifecycle.Event.ON_STOP -> {
                observers.forEach { it.onAppBackground() }
            }

            else -> {}
        }
    }

    actual fun addObserver(observer: AppLifecycleObserver) {
        observers.add(observer)
        if (!isRegistered) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
            isRegistered = true
        }
    }

    actual fun removeObserver(observer: AppLifecycleObserver) {
        observers.remove(observer)
        if (observers.isEmpty() && isRegistered) {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
            isRegistered = false
        }
    }
}
