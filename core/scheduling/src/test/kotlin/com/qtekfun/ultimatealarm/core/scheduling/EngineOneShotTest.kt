package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One-shot alarms (RF-P5, D-11): their date and time if ahead, nothing else applies. */
class EngineOneShotTest {
    private fun oneShot(day: java.time.LocalDate?, h: Int = 9) = AlarmSpec(5, LocalTime.of(h, 0), kind = AlarmKind.ONE_SHOT, oneShotDate = day)

    @Test
    fun `one-shot alarms do not respect holidays by default`() {
        assertEquals(false, oneShot(null).respectHolidays)
        assertEquals(true, weekly().respectHolidays)
    }

    @Test
    fun `future one-shot rings on its date`() {
        assertEquals(Occurrence(at(2026, 12, 24, 9), date(2026, 12, 24)), next(oneShot(date(2026, 12, 24)), at(2026, 10, 16, 8)))
    }

    @Test
    fun `past one-shot gives null`() {
        assertNull(next(oneShot(date(2026, 10, 16)), at(2026, 10, 16, 10)))
        assertNull(next(oneShot(date(2026, 10, 1)), at(2026, 10, 16, 10)))
    }

    @Test
    fun `next time it is HH MM`() {
        assertEquals(at(2026, 10, 16, 9), next(oneShot(null), at(2026, 10, 16, 8))?.at)
        assertEquals(at(2026, 10, 17, 9), next(oneShot(null), at(2026, 10, 16, 10))?.at)
    }

    @Test
    fun `rules do not touch one-shot alarms`() {
        val day = date(2026, 10, 19)
        val rules = RuleSet(
            overrides = listOf(DayOverride(5, day, OverrideMode.FORCE_OFF)),
            vacations = listOf(Vacation(1, day, day)),
            skips = listOf(SkipNext(5, day)),
            holidays = holidays(day to "x"),
            decisions = listOf(HolidayDecision(day, null, skip = true))
        )
        assertEquals(at(2026, 10, 19, 9), next(oneShot(day), at(2026, 10, 16, 8), rules)?.at)
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(oneShot(day), day, rules))
        assertEquals(DayVerdict.Off(OffReason.NOT_SCHEDULED), SchedulingEngine.explain(oneShot(day), day.plusDays(1), rules))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(oneShot(null), day, rules))
    }

    @Test
    fun `disabled one-shot never rings`() {
        assertNull(next(oneShot(date(2026, 12, 24)).copy(enabled = false), at(2026, 10, 16, 8)))
    }

    @Test
    fun `one-shot has no skip target and no suppression`() {
        assertNull(SchedulingEngine.skipTarget(oneShot(date(2026, 12, 24)), RuleSet(), at(2026, 10, 16, 8)))
        assertNull(SchedulingEngine.nextSuppression(oneShot(date(2026, 12, 24)), RuleSet(), at(2026, 10, 16, 8)))
    }
}
