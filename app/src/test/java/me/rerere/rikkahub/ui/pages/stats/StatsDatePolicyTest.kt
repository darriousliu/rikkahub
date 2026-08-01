package me.rerere.rikkahub.ui.pages.stats

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsDatePolicyTest {
    @Test
    fun `sunday keeps the same weekday before subtracting 52 weeks`() {
        assertEquals(
            LocalDate(2025, 2, 23),
            heatmapStartDate(LocalDate(2026, 2, 22)),
        )
    }

    @Test
    fun `monday first backs up to the previous sunday`() {
        assertEquals(
            LocalDate(2025, 2, 23),
            heatmapStartDate(LocalDate(2026, 2, 23)),
        )
    }

    @Test
    fun `year boundary preserves previous or same sunday policy`() {
        assertEquals(
            LocalDate(2023, 12, 31),
            heatmapStartDate(LocalDate(2025, 1, 1)),
        )
    }
}
