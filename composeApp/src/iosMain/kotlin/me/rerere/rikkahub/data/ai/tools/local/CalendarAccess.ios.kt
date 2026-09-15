@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKEntityType.EKEntityTypeEvent
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpan.EKSpanThisEvent
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

internal actual val calendarAccess: CalendarAccess = IosCalendarAccess()

internal class IosCalendarAccess : CalendarAccess {
    private val store by lazy { EKEventStore() }

    @Suppress("DEPRECATION")
    override fun hasReadPermission(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent).let {
            it == EKAuthorizationStatusFullAccess || it == EKAuthorizationStatusAuthorized
        }

    override fun hasWritePermission(): Boolean = hasReadPermission()

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        if (hasReadPermission()) return@withContext true
        suspendCancellableCoroutine { continuation ->
            val completion: (Boolean, NSError?) -> Unit = { granted, error ->
                if (continuation.isActive) {
                    if (granted) continuation.resume(true)
                    else continuation.resumeWithException(LocalToolAccessException("NO_PERMISSION",
                        error?.localizedDescription
                            ?: "Calendar access was denied. Enable full calendar access for RikkaHub in Settings."))
                }
            }
            if (UIDevice.currentDevice.systemVersion.substringBefore('.').toInt() >= 17) {
                store.requestFullAccessToEventsWithCompletion(completion)
            } else {
                @Suppress("DEPRECATION")
                store.requestAccessToEntityType(EKEntityTypeEvent, completion)
            }
        }
    }

    override suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent> =
        withContext(Dispatchers.Default) {
            val predicate = store.predicateForEventsWithStartDate(
                date(startMs), date(endMs), calendars = null,
            )
            store.eventsMatchingPredicate(predicate).filterIsInstance<EKEvent>()
                .sortedBy { it.startDate!!.timeIntervalSince1970 }
                .filter { query == null || it.title.orEmpty().contains(query, ignoreCase = true) }
                .take(limit)
                .map { event ->
                    CalendarEvent(
                        id = JsonPrimitive(event.eventIdentifier), title = event.title.orEmpty(),
                        description = event.notes.orEmpty(), location = event.location.orEmpty(),
                        startMillis = readDate(event.startDate!!, event.allDay),
                        endMillis = readDate(event.endDate!!, event.allDay), allDay = event.allDay,
                        calendar = event.calendar?.title.orEmpty(),
                    )
                }
        }

    override suspend fun defaultCalendarId(): String? = withContext(Dispatchers.Default) {
        store.defaultCalendarForNewEvents?.calendarIdentifier
    }

    override suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive? =
        withContext(Dispatchers.Default) {
            val calendar = store.calendarWithIdentifier(calendarId) ?: return@withContext null
            val nativeEvent = EKEvent.eventWithEventStore(store).apply {
                setCalendar(calendar)
                setTitle(event.title)
                setNotes(event.description)
                setLocation(event.location)
                setAllDay(event.allDay)
                setTimeZone(if (event.allDay) NSTimeZone.localTimeZone else NSTimeZone.timeZoneWithName(event.timeZoneId))
                setStartDate(writeDate(event.startMillis, event.allDay))
                setEndDate(writeDate(event.endMillis, event.allDay))
            }
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!store.saveEvent(nativeEvent, EKSpanThisEvent, commit = true, error = error.ptr)) {
                    throw IllegalStateException(error.value?.localizedDescription ?: "Failed to save calendar event.")
                }
            }
            nativeEvent.eventIdentifier?.let(::JsonPrimitive)
        }

    private fun date(millis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)

    private fun readDate(date: NSDate, allDay: Boolean): Long {
        val instant = Instant.fromEpochMilliseconds((date.timeIntervalSince1970 * 1000).toLong())
        return if (allDay) instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() else instant.toEpochMilliseconds()
    }

    private fun writeDate(millis: Long, allDay: Boolean): NSDate = date(
        if (allDay) Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() else millis,
    )
}
