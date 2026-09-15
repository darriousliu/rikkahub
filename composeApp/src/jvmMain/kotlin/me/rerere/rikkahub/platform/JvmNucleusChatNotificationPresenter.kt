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
import me.rerere.common.logging.Logging
import me.rerere.common.logging.RikkaLog as Log
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
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
    private var availabilityLogged = false
    private var lastAuthorization: Boolean? = null
    private val nativeLoggers = listOf(
        Logger.getLogger("dev.nucleusframework.notification"),
        Logger.getLogger("dev.nucleusframework.launcher.windows"),
    )
    private val nativeLogHandler = object : Handler() {
        override fun publish(record: LogRecord) {
            if (record.level.intValue() < Level.WARNING.intValue()) return
            // The common API may return Success before Windows reports an asynchronous native failure.
            Logging.log(TAG, "${record.loggerName}: ${record.message}" +
                (record.thrown?.let { "\n${it.stackTraceToString()}" } ?: ""))
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    init {
        nativeLoggers.forEach { it.addHandler(nativeLogHandler) }
        job.invokeOnCompletion {
            if (isWindows && initialized) {
                runCatching { WindowsNotificationCenter.uninitialize() }
                    .onFailure { Log.w(TAG, "Failed to release native notifications", it) }
            }
            nativeLoggers.forEach { it.removeHandler(nativeLogHandler) }
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
        Logging.log(TAG, "Generation completed; scheduling desktop notification")
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
        val available = (isMacOS || isWindows) && NotificationManager.isAvailable()
        if (!availabilityLogged) {
            Logging.log(TAG, "Native notifications available=$available, " +
                "OS=${System.getProperty("os.name")}, package=${System.getProperty("nucleus.executable.type")}")
            availabilityLogged = true
        }
        if (!available || !requestAuthorization()) {
            null
        } else {
            currentCoroutineContext().ensureActive()
            if (!initialized) {
                // notification-common ignores Windows initialize()'s Boolean result. Check it ourselves
                // so a failed shortcut/WinRT setup can be retried on the next notification.
                initialized = if (isWindows) WindowsNotificationCenter.initialize() else true
                Logging.log(TAG, "Native notification initialization succeeded=$initialized")
            }
            if (!initialized) null else when (val result = notification(
                title = title,
                message = body,
                onFailed = {
                    Logging.log(TAG, "The OS reported a notification display failure")
                    Log.w(TAG, "The OS could not display a chat notification")
                },
            ).send()) {
                is NotificationResult.Success -> result.handle
                is NotificationResult.Failure -> {
                    Logging.log(TAG, "Could not send chat notification: ${result.reason}")
                    Log.w(TAG, "Could not send chat notification: ${result.reason}")
                    null
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logging.log(TAG, "Could not send chat notification: ${e.stackTraceToString()}")
        Log.w(TAG, "Could not send chat notification", e)
        null
    } catch (e: LinkageError) {
        // Missing DLL dependencies and JNI entry points are Errors, not Exceptions.
        Logging.log(TAG, "Could not load native notifications: ${e.stackTraceToString()}")
        Log.w(TAG, "Could not load native notifications", e)
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
                    if (error != null) {
                        Logging.log(TAG, "Could not request notification permission: $error")
                        Log.w(TAG, "Could not request notification permission: $error")
                    }
                    continuation.resume(granted)
                }
            }
        }.also { authorization = it }
        return pending.await().also { granted ->
            if (lastAuthorization != granted) {
                Logging.log(TAG, "macOS notification authorization granted=$granted")
                lastAuthorization = granted
            }
        }
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
