package me.rerere.rikkahub.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.ChatLiveUpdateNotification
import me.rerere.rikkahub.platform.ChatNotificationPhase
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ChatNotificationManagerContractTest {
    @Test
    fun originalPhasePriorityAndPreviewLimitsArePreserved() = runTest {
        Fixture().use { f ->
            f.settings(true, true)
            val text = UIMessagePart.Text("old" + "t".repeat(205))
            val reasoning = UIMessagePart.Reasoning("old" + "r".repeat(205), finishedAt = null)
            val tool = UIMessagePart.Tool("id", "server__actual_tool", "i".repeat(105))
            val cases = listOf(
                listOf(tool, reasoning, text) to ChatNotificationPhase.Tool("actual_tool", "i".repeat(100)),
                listOf(reasoning, text) to ChatNotificationPhase.Thinking("r".repeat(200)),
                listOf(text) to ChatNotificationPhase.Writing("t".repeat(200)),
                emptyList<UIMessagePart>() to ChatNotificationPhase.Starting,
                listOf(tool, tool.copy(output = listOf(text)), reasoning, text) to
                    ChatNotificationPhase.Thinking("r".repeat(200)),
                listOf(reasoning, UIMessagePart.Reasoning("finished"), text) to
                    ChatNotificationPhase.Writing("t".repeat(200)),
            )
            val expected = cases.map { (parts, phase) ->
                val id = Uuid.random()
                f.update(id, parts)
                Action.Live(ChatLiveUpdateNotification(id, "sender", phase))
            }
            assertEquals(expected, f.drain())
        }
    }

    @Test
    fun foregroundAndSettingsKeepTheOriginalGatesAndCompletionOrder() = runTest {
        Fixture().use { f ->
            for (foreground in listOf(false, true)) {
                for (enabled in listOf(false, true)) {
                    for (live in listOf(false, true)) {
                        f.manager.setForeground(foreground)
                        f.settings(enabled, live)
                        for (content in listOf(null, "", "completed")) {
                            val id = Uuid.random()
                            f.update(id)
                            f.bus.emit(AppEvent.ChatGenerationEnded(id, "sender", content))
                            val expected = buildList {
                                if (!foreground && enabled && live) add(f.live(id))
                                add(Action.Cancel(id))
                                if (!foreground && enabled && content != null) add(Action.Done(id, "sender", content))
                            }
                            assertEquals(
                                expected,
                                f.drain(),
                                "foreground=$foreground enabled=$enabled live=$live content=$content",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun throttlesEachConversationForOneSecondAndResetsOnFailureOrCancellation() = runTest {
        Fixture().use { f ->
            f.settings(true, true)
            val first = Uuid.random()
            val second = Uuid.random()
            f.update(first)
            f.update(first)
            f.update(second)
            assertEquals(listOf(f.live(first), f.live(second)), f.drain())
            // Use the real monotonic clock; no production clock interface is introduced for this test.
            Thread.sleep(1_050)
            f.update(first)
            assertEquals(listOf(f.live(first)), f.drain())
            f.bus.emit(AppEvent.ChatGenerationEnded(first, "sender", null))
            f.update(first)
            assertEquals(listOf(Action.Cancel(first), f.live(first)), f.drain())
        }
    }

    @Test
    fun disposingTheManagerStopsCollectionAndClosesThePresenterOnce() = runTest {
        Fixture().use { f ->
            f.manager.close()
            f.manager.close()
            assertEquals(Action.Closed, f.queue.poll(2, TimeUnit.SECONDS))
            f.bus.emit(AppEvent.ChatGenerationEnded(Uuid.random(), "sender", "after close"))
            assertNull(f.queue.poll(100, TimeUnit.MILLISECONDS))
        }
    }

    private sealed interface Action {
        data class Live(val notification: ChatLiveUpdateNotification) : Action
        data class Done(val id: Uuid, val sender: String, val content: String) : Action
        data class Cancel(val id: Uuid) : Action
        data object Closed : Action
    }

    private class Fixture : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        private val store = SettingsStore(preferences, settingsScope)
        private val probe = Uuid.random()
        private val ready = LinkedBlockingQueue<Unit>()
        val queue = LinkedBlockingQueue<Action>()
        val bus = AppEventBus()
        val manager = ChatNotificationManager(scope, bus, store, object : ChatNotificationPresenter {
            override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {
                queue.add(Action.Live(notification))
            }
            override fun showGenerationCompleted(conversationId: Uuid, senderName: String, contentPreview: String) {
                queue.add(Action.Done(conversationId, senderName, contentPreview))
            }
            override fun cancelLiveUpdate(conversationId: Uuid) {
                if (conversationId == probe) ready.offer(Unit) else queue.add(Action.Cancel(conversationId))
            }
            override fun close() { queue.add(Action.Closed) }
        })

        init {
            // AppEventBus has no replay. Wait for its real background collector to subscribe.
            var subscribed = false
            repeat(100) {
                if (!subscribed) {
                    bus.tryEmit(AppEvent.ChatGenerationEnded(probe, "probe", null))
                    subscribed = ready.poll(20, TimeUnit.MILLISECONDS) != null
                }
            }
            check(subscribed) { "Notification collector did not subscribe" }
        }

        suspend fun settings(enabled: Boolean, live: Boolean) {
            store.update { it.copy(displaySetting = it.displaySetting.copy(
                enableNotificationOnMessageGeneration = enabled,
                enableLiveUpdateNotification = live,
            )) }
        }

        suspend fun update(id: Uuid, parts: List<UIMessagePart> = listOf(UIMessagePart.Text("text"))) {
            bus.emit(AppEvent.ChatGenerationUpdate(id, UIMessage.user("unused").copy(parts = parts), "sender"))
        }

        fun live(id: Uuid) = Action.Live(
            ChatLiveUpdateNotification(id, "sender", ChatNotificationPhase.Writing("text")),
        )

        suspend fun drain(): List<Action> {
            val barrier = Uuid.random()
            bus.emit(AppEvent.ChatGenerationEnded(barrier, "barrier", null))
            return buildList {
                while (true) {
                    val action = assertNotNull(queue.poll(2, TimeUnit.SECONDS), "Notification event was not handled")
                    if (action == Action.Cancel(barrier)) break
                    add(action)
                }
            }
        }

        override fun close() {
            manager.close()
            scope.cancel()
            settingsScope.cancel()
        }
    }
}
