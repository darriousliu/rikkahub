package me.rerere.rikkahub.data.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

public class AppEventBus {
    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    private val mutableGenerationEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)

    /** UI 和 OAuth 事件；聊天生成事件使用独立队列，避免被暂停的界面订阅者阻塞。 */
    public val events: SharedFlow<AppEvent> = mutableEvents.asSharedFlow()

    /** 由后台通知策略消费，不能与窗口的 LaunchedEffect 共用缓冲。 */
    public val generationEvents: SharedFlow<AppEvent> = mutableGenerationEvents.asSharedFlow()

    public suspend fun emit(event: AppEvent) {
        flowFor(event).emit(event)
    }

    /** 非挂起发送；缓冲满时丢弃事件并返回 false。 */
    public fun tryEmit(event: AppEvent): Boolean = flowFor(event).tryEmit(event)

    private fun flowFor(event: AppEvent): MutableSharedFlow<AppEvent> = when (event) {
        is AppEvent.ChatGenerationUpdate, is AppEvent.ChatGenerationEnded -> mutableGenerationEvents
        else -> mutableEvents
    }
}
