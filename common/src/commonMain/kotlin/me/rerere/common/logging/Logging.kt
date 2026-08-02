package me.rerere.common.logging

import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        override val tag: String,
        val message: String,
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null,
    ) : LogEntry()
}

@OptIn(ExperimentalAtomicApi::class)
internal class RecentLogStore(
    private val capacity: Int = MAX_RECENT_LOGS,
) {
    private val recentLogs = AtomicReference<List<LogEntry>>(emptyList())
    private val requestLoggingEnabled = AtomicBoolean(false)

    fun log(tag: String, message: String) {
        addLog(LogEntry.TextLog(tag = tag, message = message))
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        if (!requestLoggingEnabled.load()) return
        addLog(entry)
    }

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled.load()

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled.store(enabled)
    }

    private fun addLog(entry: LogEntry) {
        while (true) {
            val snapshot = recentLogs.load()
            val updated = if (capacity <= 0) {
                emptyList()
            } else {
                buildList(minOf(capacity, snapshot.size + 1)) {
                    add(entry)
                    addAll(snapshot.take(capacity - 1))
                }
            }
            if (recentLogs.compareAndSet(snapshot, updated)) return
        }
    }

    fun getRecentLogs(): List<LogEntry> = recentLogs.load()

    fun getTextLogs(): List<LogEntry.TextLog> = recentLogs.load().filterIsInstance<LogEntry.TextLog>()

    fun getRequestLogs(): List<LogEntry.RequestLog> = recentLogs.load().filterIsInstance<LogEntry.RequestLog>()

    fun clear() {
        while (true) {
            val snapshot = recentLogs.load()
            if (snapshot.isEmpty() || recentLogs.compareAndSet(snapshot, emptyList())) return
        }
    }
}

object Logging {
    private val store = RecentLogStore()

    fun log(tag: String, message: String) = store.log(tag, message)

    fun logRequest(entry: LogEntry.RequestLog) = store.logRequest(entry)

    fun isRequestLoggingEnabled(): Boolean = store.isRequestLoggingEnabled()

    fun setRequestLoggingEnabled(enabled: Boolean) = store.setRequestLoggingEnabled(enabled)

    fun getRecentLogs(): List<LogEntry> = store.getRecentLogs()

    fun getTextLogs(): List<LogEntry.TextLog> = store.getTextLogs()

    fun getRequestLogs(): List<LogEntry.RequestLog> = store.getRequestLogs()

    fun clear() = store.clear()
}
