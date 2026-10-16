package com.qtekfun.ultimatealarm.core.scheduling

import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Holidays: no decision rings, a skip decision silences, per-alarm decisions, alarms that ignore holidays. */
class EngineHolidayTest {
    private val pilar = date(2026, 10, 12) // Monday, Fiesta Nacional de España
    private val calendar = holidays(pilar to "Fiesta Nacional de España")
    private val alarm = weekly()

    @Test
    fun `holiday without a decision rings`() {
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, RuleSet(holidays = calendar)))
        assertEquals(at(2026, 10, 12, 7), next(alarm, at(2026, 10, 10, 9), RuleSet(holidays = calendar))?.at)
    }

    @Test
    fun `skip decision for every alarm silences it`() {
        val rules = RuleSet(holidays = calendar, decisions = listOf(HolidayDecision(pilar, null, skip = true)))
        assertEquals(DayVerdict.Off(OffReason.HOLIDAY), SchedulingEngine.explain(alarm, pilar, rules))
        assertEquals(at(2026, 10, 13, 7), next(alarm, at(2026, 10, 10, 9), rules)?.at)
    }

    @Test
    fun `keep decision rings`() {
        val rules = RuleSet(holidays = calendar, decisions = listOf(HolidayDecision(pilar, null, skip = false)))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, rules))
    }

    @Test
    fun `per alarm decision beats the global one`() {
        val rules = RuleSet(
            holidays = calendar,
            decisions = listOf(HolidayDecision(pilar, null, skip = true), HolidayDecision(pilar, 1, skip = false))
        )
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, rules))
        assertEquals(DayVerdict.Off(OffReason.HOLIDAY), SchedulingEngine.explain(weekly(id = 2), pilar, rules))
    }

    @Test
    fun `decision of another alarm does not apply`() {
        val rules = RuleSet(holidays = calendar, decisions = listOf(HolidayDecision(pilar, 2, skip = true)))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, rules))
    }

    @Test
    fun `alarm that ignores holidays rings even with a skip decision`() {
        val rules = RuleSet(holidays = calendar, decisions = listOf(HolidayDecision(pilar, null, skip = true)))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(weekly(respectHolidays = false), pilar, rules))
    }

    @Test
    fun `decision on a day that is not a holiday is ignored`() {
        val rules = RuleSet(decisions = listOf(HolidayDecision(pilar, null, skip = true)))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, rules))
    }

    @Test
    fun `holiday on a non scheduled day stays not scheduled`() {
        val sunday = date(2026, 10, 18)
        val rules = RuleSet(holidays = holidays(sunday to "x"), decisions = listOf(HolidayDecision(sunday, null, skip = true)))
        // A decision only matters on days the alarm would ring; the HOLIDAY reason is still reported first.
        assertEquals(DayVerdict.Off(OffReason.HOLIDAY), SchedulingEngine.explain(alarm, sunday, rules))
        assertNull(SchedulingEngine.nextSuppression(alarm, rules, at(2026, 10, 17, 9)))
    }

    @Test
    fun `api holiday cancelled by the user rings`() {
        val cal = EffectiveHolidays(api = listOf(Holiday(pilar, "Pilar")), overrides = listOf(HolidayOverride(pilar, isHoliday = false)))
        val rules = RuleSet(holidays = cal, decisions = listOf(HolidayDecision(pilar, null, skip = true)))
        assertEquals(DayVerdict.Rings, SchedulingEngine.explain(alarm, pilar, rules))
    }

    @Test
    fun `manual and yearly holidays count`() {
        val local = date(2026, 5, 15) // San Isidro, a Friday
        val cal = EffectiveHolidays(yearly = listOf(YearlyHoliday(MonthDay.of(5, 15), "San Isidro")))
        val rules = RuleSet(holidays = cal, decisions = listOf(HolidayDecision(local, null, skip = true)))
        assertEquals(DayVerdict.Off(OffReason.HOLIDAY), SchedulingEngine.explain(alarm, local, rules))
    }
}
