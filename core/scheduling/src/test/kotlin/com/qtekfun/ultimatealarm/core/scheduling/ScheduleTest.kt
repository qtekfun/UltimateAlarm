package com.qtekfun.ultimatealarm.core.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    @Test
    fun `weekly rings only on its days`() {
        val workdays = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(workdays.isScheduledOn(LocalDate.of(2026, 10, 12))) // Monday
        assertFalse(workdays.isScheduledOn(LocalDate.of(2026, 10, 13))) // Tuesday
        assertTrue(workdays.isScheduledOn(LocalDate.of(2026, 10, 16))) // Friday
    }

    @Test
    fun `empty weekly never rings`() {
        assertFalse(Schedule.Weekly(emptySet()).isScheduledOn(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `four on four off cycle repeats from the anchor`() {
        val anchor = LocalDate.of(2026, 10, 1)
        val cycle = Schedule.Cycle(anchor, List(4) { true } + List(4) { false })
        val rings = (0L until 16L).map { cycle.isScheduledOn(anchor.plusDays(it)) }
        assertEquals(List(2) { List(4) { true } + List(4) { false } }.flatten(), rings)
    }

    @Test
    fun `cycle extends before the anchor`() {
        val anchor = LocalDate.of(2026, 10, 1)
        val cycle = Schedule.Cycle(anchor, listOf(true, false, false))
        assertTrue(cycle.isScheduledOn(anchor.minusDays(3)))
        assertFalse(cycle.isScheduledOn(anchor.minusDays(1)))
        assertFalse(cycle.isScheduledOn(anchor.minusDays(2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycle without days is rejected`() {
        Schedule.Cycle(LocalDate.of(2026, 10, 1), emptyList())
    }
}
