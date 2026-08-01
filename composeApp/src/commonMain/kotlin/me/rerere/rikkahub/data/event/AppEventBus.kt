package me.rerere.rikkahub.data.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

public class AppEventBus {
    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)

    public val events: SharedFlow<AppEvent> = mutableEvents.asSharedFlow()

    public suspend fun emit(event: AppEvent) {
        mutableEvents.emit(event)
    }

    /** 非挂起发送；缓冲满时丢弃事件并返回 false。 */
    public fun tryEmit(event: AppEvent): Boolean = mutableEvents.tryEmit(event)
}
