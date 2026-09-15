package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.context
import kotlinx.serialization.json.JsonPrimitive

internal actual val calendarAccess: CalendarAccess by lazy { AndroidCalendarAccess(FileKit.context) }

internal class AndroidCalendarAccess(private val context: Context) : CalendarAccess {
    override fun hasReadPermission(): Boolean = hasCalendarReadPermission(context)
    override fun hasWritePermission(): Boolean = hasCalendarWritePermission(context)
    // Android's permission dialog remains in PermissionManager.
    override suspend fun requestPermission(): Boolean = hasWritePermission()
    override suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val selection = if (query != null) {
            "${CalendarContract.Instances.TITLE} LIKE ?"
        } else null
        val selectionArgs = if (query != null) {
            arrayOf("%$query%")
        } else null

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        return buildList {
            context.contentResolver.query(uri, projection, selection, selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    add(CalendarEvent(
                        id = JsonPrimitive(cursor.getLong(0)),
                        title = cursor.getString(1) ?: "",
                        description = cursor.getString(2) ?: "",
                        location = cursor.getString(3) ?: "",
                        startMillis = cursor.getLong(4),
                        endMillis = cursor.getLong(5),
                        allDay = cursor.getInt(6) == 1,
                        calendar = cursor.getString(7) ?: "",
                    ))
                    count++
                }
            }
        }
    }
    override suspend fun defaultCalendarId(): String? = getDefaultCalendarId(context)?.toString()
    override suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId.toLong())
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZoneId)
            if (event.allDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
            }
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.let { JsonPrimitive(ContentUris.parseId(it)) }
    }
}

private fun hasCalendarReadPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

private fun hasCalendarWritePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

private fun getDefaultCalendarId(context: Context): Long? {
    val projection = arrayOf(CalendarContract.Calendars._ID)
    val writableSelection =
        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"
    val writableArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        "$writableSelection AND ${CalendarContract.Calendars.IS_PRIMARY} = 1",
        writableArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getLong(0)
    }
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        writableSelection,
        writableArgs,
        "${CalendarContract.Calendars.VISIBLE} DESC"
    )?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getLong(0)
    }
    return null
}
