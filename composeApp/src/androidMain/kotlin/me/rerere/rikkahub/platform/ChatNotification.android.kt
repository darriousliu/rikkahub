package me.rerere.rikkahub.platform

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

internal actual fun observeChatNotificationForeground(onChanged: (Boolean) -> Unit): () -> Unit {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> onChanged(true)
            Lifecycle.Event.ON_STOP -> onChanged(false)
            else -> {}
        }
    }
    lifecycle.addObserver(observer)
    return { lifecycle.removeObserver(observer) }
}

internal actual fun notificationTimeMillis(): Long = SystemClock.elapsedRealtime()
