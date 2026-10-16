package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleToolsTest {
    @Test
    fun `presets build their masks from the anchor`() {
        val cycle = ScheduleTools.shift(ShiftPreset.FOUR_FOUR, LocalDate.of(2026, 10, 1))
        assertEquals(8, cycle.mask.size)
        assertTrue(cycle.isScheduledOn(LocalDate.of(2026, 10, 4)))
        assertFalse(cycle.isScheduledOn(LocalDate.of(2026, 10, 5)))
        assertTrue(cycle.isScheduledOn(LocalDate.of(2026, 10, 9)))
        assertEquals(listOf(true, true, true, true, true, false, false), ShiftPreset.FIVE_TWO.mask())
        assertEquals(9, ShiftPreset.SIX_THREE.mask().size)
        assertEquals(4, ShiftPreset.TWO_TWO.mask().size)
    }

    @Test
    fun `alternate weeks ring one week in two`() {
        // Anchor on a Wednesday: the cycle starts on that week's Monday, 2026-10-12.
        val cycle = ScheduleTools.alternateWeeks(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 10, 14))
        assertEquals(LocalDate.of(2026, 10, 12), cycle.anchor)
        assertTrue(cycle.isScheduledOn(LocalDate.of(2026, 10, 12)))
        assertTrue(cycle.isScheduledOn(LocalDate.of(2026, 10, 16)))
        assertFalse(cycle.isScheduledOn(LocalDate.of(2026, 10, 13)))
        assertFalse(cycle.isScheduledOn(LocalDate.of(2026, 10, 19)))
        assertFalse(cycle.isScheduledOn(LocalDate.of(2026, 10, 23)))
        assertTrue(cycle.isScheduledOn(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `resize keeps what fits and clamps`() {
        assertEquals(listOf(true, false, false), ScheduleTools.resize(listOf(true, false), 3))
        assertEquals(listOf(true, false), ScheduleTools.resize(listOf(true, false, true), 1))
        assertEquals(28, ScheduleTools.resize(emptyList(), 99).size)
    }

    @Test
    fun `override cycle`() {
        assertEquals(OverrideMode.FORCE_ON, ScheduleTools.nextOverride(null))
        assertEquals(OverrideMode.FORCE_OFF, ScheduleTools.nextOverride(OverrideMode.FORCE_ON))
        assertNull(ScheduleTools.nextOverride(OverrideMode.FORCE_OFF))
    }

    @Test
    fun `month grid starts on Monday and pads both ends`() {
        val grid = ScheduleTools.monthGrid(YearMonth.of(2026, 10)) // 1 Oct 2026 is a Thursday, 31 days
        assertEquals(5, grid.size)
        assertEquals(listOf(null, null, null), grid[0].take(3))
        assertEquals(LocalDate.of(2026, 10, 1), grid[0][3])
        assertEquals(LocalDate.of(2026, 10, 31), grid[4][5])
        assertNull(grid[4][6])
        assertTrue(grid.all { it.size == 7 })
        // February 2027 starts on a Monday and has exactly four weeks.
        assertEquals(4, ScheduleTools.monthGrid(YearMonth.of(2027, 2)).size)
    }
}
