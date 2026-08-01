package me.rerere.rikkahub.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus

public actual class ChatNotificationManager actual constructor() {
    private val foreground = MutableStateFlow(false)
    private var coordinator: ChatNotificationCoordinator? = null

    public actual fun start(
        scope: CoroutineScope,
        eventBus: AppEventBus,
        settingsStore: SettingsStore,
        presenter: ChatNotificationPresenter,
    ) {
        close()
        coordinator = ChatNotificationCoordinator(
            scope = scope,
            eventBus = eventBus,
            settingsStore = settingsStore,
            foreground = foreground,
            presenter = presenter,
            monotonicMillis = { System.nanoTime() / 1_000_000L },
        )
    }

    public actual fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
    }

    public actual fun close() {
        coordinator?.close()
        coordinator = null
    }
}
