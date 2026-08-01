package me.rerere.common.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun Instant.toCalendarDate(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDate = toLocalDateTime(timeZone).date

fun Clock.today(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDate = now().toCalendarDate(timeZone)
