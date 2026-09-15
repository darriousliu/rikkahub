package me.rerere.rikkahub.data.ai.tools.local

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

/** EventKit calls only; tool arguments, approval and response formatting remain in common. */
internal class MacCalendarAccess : CalendarAccess {
    private val store by lazy { MacCalendarObjC.pointer(MacCalendarObjC.cls("EKEventStore"), "new")!! }

    override fun hasReadPermission(): Boolean = MacCalendarObjC.number(
        MacCalendarObjC.cls("EKEventStore"), "authorizationStatusForEntityType:", 0L,
    ) == 3L // EKAuthorizationStatusFullAccess (formerly Authorized).

    override fun hasWritePermission(): Boolean = hasReadPermission()

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        if (hasReadPermission()) return@withContext true
        suspendCancellableCoroutine { continuation ->
            val block = CalendarPermissionBlock { granted, error ->
                if (continuation.isActive) {
                    if (granted) continuation.resume(true)
                    else continuation.resumeWithException(LocalToolAccessException("NO_PERMISSION",
                        error ?: "Calendar access was denied. Enable RikkaHub in System Settings → Privacy & Security → Calendars."))
                }
            }
            val selector = "requestFullAccessToEventsWithCompletion:"
            if (MacCalendarObjC.boolean(store, "respondsToSelector:", MacCalendarObjC.selector(selector))) {
                MacCalendarObjC.call(store, selector, block.memory)
            } else {
                MacCalendarObjC.call(store, "requestAccessToEntityType:completion:", 0L, block.memory)
            }
            // EventKit does not expose cancellation. Retain the block until its callback even if the coroutine stops.
        }
    }

    override suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            MacCalendarObjC.pool {
                val predicate = MacCalendarObjC.pointer(store, "predicateForEventsWithStartDate:endDate:calendars:",
                    date(startMs), date(endMs), null)
                val events = MacCalendarObjC.pointer(store, "eventsMatchingPredicate:", predicate)
                val count = MacCalendarObjC.number(events, "count").toInt()
                (0 until count).map { index ->
                    val event = MacCalendarObjC.pointer(events, "objectAtIndex:", index.toLong())
                    val allDay = MacCalendarObjC.boolean(event, "isAllDay")
                    CalendarEvent(
                        id = JsonPrimitive(MacCalendarObjC.string(event, "eventIdentifier")),
                        title = MacCalendarObjC.string(event, "title"),
                        description = MacCalendarObjC.string(event, "notes"),
                        location = MacCalendarObjC.string(event, "location"),
                        startMillis = readDate(MacCalendarObjC.pointer(event, "startDate"), allDay),
                        endMillis = readDate(MacCalendarObjC.pointer(event, "endDate"), allDay),
                        allDay = allDay,
                        calendar = MacCalendarObjC.string(MacCalendarObjC.pointer(event, "calendar"), "title"),
                    )
                }.sortedBy { it.startMillis }
                    .filter { query == null || it.title.contains(query, ignoreCase = true) }
                    .take(limit)
            }
        }

    override suspend fun defaultCalendarId(): String? = withContext(Dispatchers.IO) {
        MacCalendarObjC.pool {
            MacCalendarObjC.pointer(store, "defaultCalendarForNewEvents")?.let {
                MacCalendarObjC.string(it, "calendarIdentifier")
            }
        }
    }

    override suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive? =
        withContext(Dispatchers.IO) {
            MacCalendarObjC.pool {
                val calendar = MacCalendarObjC.pointer(store, "calendarWithIdentifier:", MacCalendarObjC.text(calendarId))
                    ?: return@pool null
                val nativeEvent = MacCalendarObjC.pointer(MacCalendarObjC.cls("EKEvent"), "eventWithEventStore:", store)
                MacCalendarObjC.call(nativeEvent, "setCalendar:", calendar)
                MacCalendarObjC.call(nativeEvent, "setTitle:", MacCalendarObjC.text(event.title))
                MacCalendarObjC.call(nativeEvent, "setNotes:", MacCalendarObjC.text(event.description))
                MacCalendarObjC.call(nativeEvent, "setLocation:", MacCalendarObjC.text(event.location))
                MacCalendarObjC.call(nativeEvent, "setAllDay:", if (event.allDay) 1 else 0)
                val timeZone = if (event.allDay) MacCalendarObjC.pointer(MacCalendarObjC.cls("NSTimeZone"), "localTimeZone")
                else MacCalendarObjC.pointer(MacCalendarObjC.cls("NSTimeZone"), "timeZoneWithName:", MacCalendarObjC.text(event.timeZoneId))
                MacCalendarObjC.call(nativeEvent, "setTimeZone:", timeZone)
                MacCalendarObjC.call(nativeEvent, "setStartDate:", writeDate(event.startMillis, event.allDay))
                MacCalendarObjC.call(nativeEvent, "setEndDate:", writeDate(event.endMillis, event.allDay))
                val error = PointerByReference()
                if (!MacCalendarObjC.boolean(store, "saveEvent:span:commit:error:", nativeEvent, 0L, 1, error)) {
                    throw IllegalStateException(error.value?.let { MacCalendarObjC.string(it, "localizedDescription") }
                        ?: "Failed to save calendar event.")
                }
                JsonPrimitive(MacCalendarObjC.string(nativeEvent, "eventIdentifier"))
            }
        }

    private fun date(millis: Long): Pointer? = MacCalendarObjC.pointer(
        MacCalendarObjC.cls("NSDate"), "dateWithTimeIntervalSince1970:", millis / 1000.0,
    )

    private fun readDate(date: Pointer?, allDay: Boolean): Long {
        val instant = Instant.fromEpochMilliseconds((MacCalendarObjC.double(date, "timeIntervalSince1970") * 1000).toLong())
        return if (allDay) instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() else instant.toEpochMilliseconds()
    }

    private fun writeDate(millis: Long, allDay: Boolean): Pointer? = date(
        if (allDay) Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() else millis,
    )
}

private object MacCalendarObjC {
    private val eventKit = NativeLibrary.getInstance("/System/Library/Frameworks/EventKit.framework/EventKit")
    private val objc = NativeLibrary.getInstance("objc")
    private val send = objc.getFunction("objc_msgSend")
    fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    fun selector(name: String): Pointer = objc.getFunction("sel_registerName").invokePointer(arrayOf(name))
    fun pointer(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? =
        send.invokePointer(arrayOf(receiver, selector(selector), *args))
    fun number(receiver: Pointer?, selector: String, vararg args: Any?): Long =
        send.invokeLong(arrayOf(receiver, selector(selector), *args))
    fun boolean(receiver: Pointer?, selector: String, vararg args: Any?): Boolean =
        send.invokeInt(arrayOf(receiver, selector(selector), *args)) and 0xff != 0
    fun double(receiver: Pointer?, selector: String): Double = send.invokeDouble(arrayOf(receiver, selector(selector)))
    fun call(receiver: Pointer?, selector: String, vararg args: Any?) =
        send.invokeVoid(arrayOf(receiver, selector(selector), *args))
    fun text(value: String): Pointer? = pointer(cls("NSString"), "stringWithUTF8String:", value)
    fun string(receiver: Pointer?, selector: String): String =
        pointer(pointer(receiver, selector), "UTF8String")?.getString(0, "UTF-8").orEmpty()
    inline fun <T> pool(block: () -> T): T {
        val pool = pointer(cls("NSAutoreleasePool"), "new")
        return try { block() } finally { call(pool, "drain") }
    }
}

private fun interface CalendarPermissionCallback : Callback {
    fun invoke(block: Pointer, granted: Byte, error: Pointer?)
}

/** The only EventKit block needed by the JVM bridge: void (^)(BOOL, NSError *). */
private class CalendarPermissionBlock(completion: (Boolean, String?) -> Unit) {
    val memory = Memory(32)
    private val signature = Memory(8).apply { setString(0, "v@?B@", "UTF-8") }
    private val descriptor = Memory(24).apply {
        setLong(0, 0)
        setLong(8, 32)
        setPointer(16, signature)
    }
    private val callback = CalendarPermissionCallback { _, granted, error ->
        try {
            completion(granted.toInt() != 0, error?.let { MacCalendarObjC.string(it, "localizedDescription") })
        } finally {
            pending.remove(Pointer.nativeValue(memory))
        }
    }

    init {
        memory.setPointer(0, system.getGlobalVariableAddress("_NSConcreteGlobalBlock"))
        memory.setInt(8, (1 shl 28) or (1 shl 30)) // BLOCK_IS_GLOBAL | BLOCK_HAS_SIGNATURE
        memory.setInt(12, 0)
        memory.setPointer(16, CallbackReference.getFunctionPointer(callback))
        memory.setPointer(24, descriptor)
        pending[Pointer.nativeValue(memory)] = this
    }

    companion object {
        private val system = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib")
        private val pending = ConcurrentHashMap<Long, CalendarPermissionBlock>()
    }
}
