package me.rerere.rikkahub.service

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ChatServiceSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun creatingSessionWhileConversationJobsAreCollectedDoesNotBlockMainThread() {
        val koin = GlobalContext.get()
        val chatRuntime = koin.get<ChatRuntime>()
        val chatService = koin.get<ChatService>()
        assertSame("ChatRuntime must resolve to the real Android ChatService", chatService, chatRuntime)

        val conversationId = Uuid.random()
        val snapshots = CopyOnWriteArrayList<Map<Uuid, Job?>>()
        val initialSnapshotReceived = CountDownLatch(1)
        val postCreationSnapshotReceived = CountDownLatch(1)
        val creationStarted = AtomicBoolean(false)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        try {
            instrumentation.runOnMainSync {
                collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    chatRuntime.getConversationJobs().collect { snapshot ->
                        snapshots += snapshot
                        if (creationStarted.get()) {
                            postCreationSnapshotReceived.countDown()
                        } else {
                            initialSnapshotReceived.countDown()
                        }
                    }
                }
            }
            assertTrue(
                "getConversationJobs collector did not receive its initial snapshot",
                initialSnapshotReceived.await(5, TimeUnit.SECONDS),
            )

            val snapshotCountBeforeCreation = snapshots.size
            val createSession = FutureTask {
                creationStarted.set(true)
                chatRuntime.getConversationFlow(conversationId)
            }
            // A plain main-thread callback reproduces synchronous collector re-entry, with a bounded wait.
            Handler(Looper.getMainLooper()).post(createSession)
            val firstFlow = createSession.get(5, TimeUnit.SECONDS)

            assertEquals(conversationId, firstFlow.value.id)
            assertTrue(
                "The jobs collector did not receive the session-version publication",
                postCreationSnapshotReceived.await(5, TimeUnit.SECONDS),
            )
            assertTrue(snapshots.size > snapshotCountBeforeCreation)
            assertTrue(
                "Sessions without an active generation job must be filtered from the jobs snapshot",
                snapshots.last().isEmpty(),
            )

            instrumentation.waitForIdleSync()
            val snapshotCountAfterCreation = snapshots.size
            lateinit var secondFlow: StateFlow<Conversation>
            instrumentation.runOnMainSync {
                secondFlow = chatRuntime.getConversationFlow(conversationId)
            }
            instrumentation.waitForIdleSync()

            assertSame("Repeated lookup must reuse the same session state", firstFlow, secondFlow)
            assertEquals(firstFlow.value, secondFlow.value)
            assertEquals(
                "Repeated lookup must not publish another session creation",
                snapshotCountAfterCreation,
                snapshots.size,
            )
        } finally {
            collectorScope.cancel()
        }
    }
}
