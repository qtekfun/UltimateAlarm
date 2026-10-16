package com.qtekfun.ultimatealarm.core.scheduling

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Base patterns: weekly and cycles, disabled alarms, the horizon. 2026-10-16 is a Friday. */
class EngineBasicsTest {
    @Test
    fun `weekly rings later today when the time is ahead`() {
        assertEquals(at(2026, 10, 16, 7), next(weekly(), at(2026, 10, 16, 6))?.at)
    }

    @Test
    fun `weekly skips the weekend`() {
        assertEquals(at(2026, 10, 19, 7), next(weekly(), at(2026, 10, 16, 8))?.at)
    }

    @Test
    fun `occurrence carries its date`() {
        assertEquals(date(2026, 10, 19), next(weekly(), at(2026, 10, 16, 8))?.date)
    }

    @Test
    fun `exactly at the alarm time the next one is the following day`() {
        assertEquals(at(2026, 10, 19, 7), next(weekly(), at(2026, 10, 16, 7))?.at)
    }

    @Test
    fun `weekend-only alarm`() {
        val alarm = weekly(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), h = 10)
        assertEquals(at(2026, 10, 17, 10), next(alarm, at(2026, 10, 16, 6))?.at)
        assertEquals(at(2026, 10, 18, 10), next(alarm, at(2026, 10, 17, 11))?.at)
    }

    @Test
    fun `alarm with no days never rings`() {
        assertNull(next(weekly(days = emptySet()), at(2026, 10, 16, 6)))
    }

    @Test
    fun `disabled alarm never rings and says why`() {
        val alarm = weekly().copy(enabled = false)
        assertNull(next(alarm, at(2026, 10, 16, 6)))
        assertEquals(DayVerdict.Off(OffReason.DISABLED), SchedulingEngine.explain(alarm, date(2026, 10, 16), RuleSet()))
        assertNull(SchedulingEngine.nextSuppression(alarm, RuleSet(), at(2026, 10, 16, 6)))
    }

    @Test
    fun `four on four off cycle`() {
        val alarm = AlarmSpec(1, LocalTime.of(6, 0), schedule = Schedule.Cycle(date(2026, 10, 1), List(4) { true } + List(4) { false }))
        // Days 1-4 on, 5-8 off, 9-12 on.
        assertEquals(at(2026, 10, 9, 6), next(alarm, at(2026, 10, 4, 7))?.at)
        assertEquals(at(2026, 10, 3, 6), next(alarm, at(2026, 10, 2, 7))?.at)
    }

    @Test
    fun `alternate weeks as a fourteen day cycle`() {
        val mask = List(5) { true } + List(9) { false }
        val alarm = AlarmSpec(1, LocalTime.of(7, 0), schedule = Schedule.Cycle(date(2026, 10, 5), mask))
        assertEquals(at(2026, 10, 19, 7), next(alarm, at(2026, 10, 9, 8))?.at)
    }

    @Test
    fun `anchor in the future works backwards`() {
        val alarm = AlarmSpec(1, LocalTime.of(7, 0), schedule = Schedule.Cycle(date(2027, 1, 1), listOf(true, false)))
        // 2027-01-01 rings; going back 2 days at a time: 2026-12-30, ... 2026-10-16 is 77 days before -> odd -> off.
        assertEquals(at(2026, 10, 17, 7), next(alarm, at(2026, 10, 16, 6))?.at)
    }

    @Test
    fun `nothing within the horizon gives null`() {
        val alarm = weekly().copy(id = 9)
        val vacation = Vacation(1, date(2026, 10, 16), date(2028, 1, 1))
        assertNull(next(alarm, at(2026, 10, 16, 6), RuleSet(vacations = listOf(vacation))))
    }

    @Test
    fun `last day of the horizon still counts`() {
        val now = at(2026, 10, 16, 8)
        val lastDay = now.toLocalDate().plusDays(SchedulingEngine.HORIZON_DAYS)
        val alarm = weekly(days = DayOfWeek.entries.toSet())
        val vacation = Vacation(1, now.toLocalDate(), lastDay.minusDays(1))
        assertEquals(lastDay, next(alarm, now, RuleSet(vacations = listOf(vacation)))?.date)
    }
}
