package me.rerere.rikkahub.platform

import dev.nucleusframework.notification.AuthorizationOption
import dev.nucleusframework.notification.NotificationCenter
import dev.nucleusframework.notification.common.NotificationHandle
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.NotificationResult
import dev.nucleusframework.notification.common.notification
import dev.nucleusframework.notification.windows.WindowsNotificationCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.logging.RikkaLog as Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

public class JvmNucleusChatNotificationPresenter(appScope: CoroutineScope) : ChatNotificationPresenter {
    private val job = SupervisorJob(appScope.coroutineContext[Job])
    // Nucleus marshals native calls itself; notification work must not block the Tao UI thread.
    private val scope = CoroutineScope(appScope.coroutineContext + job + Dispatchers.Default.limitedParallelism(1))
    private val liveUpdates = ConcurrentHashMap<Uuid, Job>()
    private val isMacOS = System.getProperty("os.name").startsWith("Mac")
    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    // Only accessed by the serial notification dispatcher. Share a pending macOS permission prompt.
    private var authorization: Deferred<Boolean>? = null
    private var initialized = false

    init {
        job.invokeOnCompletion {
            if (isWindows && initialized) {
                runCatching { WindowsNotificationCenter.uninitialize() }
                    .onFailure { Log.w(TAG, "Failed to release native notifications", it) }
            }
        }
    }

    override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {
        val (status, body) = notification.phase.toDisplayContent()
        liveUpdates.remove(notification.conversationId)?.cancel()
        val update = scope.launch(start = CoroutineStart.LAZY) {
            val handle = showMessage("${notification.senderName} · $status", body) ?: return@launch
            try {
                awaitCancellation()
            } finally {
                // Each common API send creates a new OS notification; retire the preceding live update.
                runCatching { handle.dismiss() }
                    .onFailure { Log.w(TAG, "Failed to dismiss live notification", it) }
            }
        }
        liveUpdates[notification.conversationId] = update
        update.start()
    }

    override fun showGenerationCompleted(conversationId: Uuid, senderName: String, contentPreview: String) {
        cancelLiveUpdate(conversationId)
        scope.launch { showMessage(senderName, contentPreview) }
    }

    override fun cancelLiveUpdate(conversationId: Uuid) {
        liveUpdates.remove(conversationId)?.cancel()
    }

    override fun close() {
        scope.cancel()
        liveUpdates.clear()
    }

    private suspend fun showMessage(title: String, body: String): NotificationHandle? = try {
        if ((!isMacOS && !isWindows) || !NotificationManager.isAvailable() || !requestAuthorization()) {
            null
        } else {
            currentCoroutineContext().ensureActive()
            if (!initialized) {
                // Windows resolves its AUMID and Start Menu shortcut from Nucleus package metadata.
                NotificationManager.initialize()
                initialized = true
            }
            when (val result = notification(
                title = title,
                message = body,
                onFailed = { Log.w(TAG, "The OS could not display a chat notification") },
            ).send()) {
                is NotificationResult.Success -> result.handle
                is NotificationResult.Failure -> {
                    Log.w(TAG, "Could not send chat notification: ${result.reason}")
                    null
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not send chat notification", e)
        null
    }

    private suspend fun requestAuthorization(): Boolean {
        if (!isMacOS) return true
        // Re-check after each completed request so changes in System Settings take effect without restarting.
        val pending = authorization?.takeUnless { it.isCompleted } ?: scope.async {
            suspendCancellableCoroutine { continuation ->
                NotificationCenter.requestAuthorization(
                    options = setOf(AuthorizationOption.ALERT, AuthorizationOption.SOUND),
                ) { granted, error ->
                    if (error != null) Log.w(TAG, "Could not request notification permission: $error")
                    continuation.resume(granted)
                }
            }
        }.also { authorization = it }
        return pending.await()
    }

    private fun ChatNotificationPhase.toDisplayContent(): Pair<String, String> = when (this) {
        is ChatNotificationPhase.Tool -> "Running tool: $toolName" to inputPreview
        is ChatNotificationPhase.Thinking -> "Thinking…" to preview
        is ChatNotificationPhase.Writing -> "Writing response…" to preview
        ChatNotificationPhase.Starting -> "Generating response…" to ""
    }

    private companion object {
        const val TAG = "ChatNotification"
    }
}
