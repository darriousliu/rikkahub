package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonPrimitive

internal data class CalendarEvent(
    val id: JsonPrimitive,
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendar: String,
)

// All-day dates use UTC midnight, as in CalendarContract; native adapters convert to their date convention.
internal data class CalendarEventDraft(
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val timeZoneId: String,
    val allDay: Boolean,
)

internal interface CalendarAccess {
    fun hasReadPermission(): Boolean
    fun hasWritePermission(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent>
    suspend fun defaultCalendarId(): String?
    suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive?
}

internal expect val calendarAccess: CalendarAccess
