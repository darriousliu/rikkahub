package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isLinux

internal actual val calendarAccess: CalendarAccess by lazy {
    when {
        currentPlatformKind.isLinux -> UnavailableCalendarAccess("Calendar tools are unavailable on Linux.")
        System.getProperty("os.name").contains("mac", ignoreCase = true) -> MacCalendarAccess()
        else -> UnavailableCalendarAccess("Windows system calendar access is not yet implemented for this application package.")
    }
}

internal actual val screenTimeAccess: ScreenTimeAccess
    get() = error("Screen Time tools are unavailable on desktop.")

private class UnavailableCalendarAccess(private val reason: String) : CalendarAccess {
    override fun hasReadPermission() = false
    override fun hasWritePermission() = false
    override suspend fun requestPermission(): Boolean = throw LocalToolAccessException("UNAVAILABLE", reason)
    override suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent> =
        throw LocalToolAccessException("UNAVAILABLE", reason)
    override suspend fun defaultCalendarId(): String? = null
    override suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive? = null
}
