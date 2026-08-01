package me.rerere.rikkahub.platform

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus

public actual class ChatNotificationManager actual constructor() {
    private val foreground = MutableStateFlow(false)
    private var coordinator: ChatNotificationCoordinator? = null
    private var lifecycleObserver: LifecycleEventObserver? = null

    public actual fun start(
        scope: CoroutineScope,
        eventBus: AppEventBus,
        settingsStore: SettingsStore,
        presenter: ChatNotificationPresenter,
    ) {
        close()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground.value = true
                Lifecycle.Event.ON_STOP -> foreground.value = false
                else -> Unit
            }
        }
        lifecycleObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        foreground.value = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        coordinator = ChatNotificationCoordinator(
            scope = scope,
            eventBus = eventBus,
            settingsStore = settingsStore,
            foreground = foreground,
            presenter = presenter,
            monotonicMillis = SystemClock::elapsedRealtime,
        )
    }

    public actual fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
    }

    public actual fun close() {
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        coordinator?.close()
        coordinator = null
    }
}
