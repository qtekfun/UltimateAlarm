package com.qtekfun.ultimatealarm.core.scheduling

import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Suppression shown on the row, "skip tomorrow" target, consumed skips, holiday eve, effective holidays. */
class EngineHelpersTest {
    private val alarm = weekly()

    @Test
    fun `no suppression when the next occurrence is the next pattern day`() {
        assertNull(SchedulingEngine.nextSuppression(alarm, RuleSet(), at(2026, 10, 16, 6)))
    }

    @Test
    fun `suppression names the holiday`() {
        val monday = date(2026, 10, 19)
        val rules = RuleSet(holidays = holidays(monday to "Fiesta"), decisions = listOf(HolidayDecision(monday, null, true)))
        assertEquals(Suppression(monday, OffReason.HOLIDAY, Holiday(monday, "Fiesta")), SchedulingEngine.nextSuppression(alarm, rules, at(2026, 10, 16, 8)))
    }

    @Test
    fun `suppression reports skip, vacation and exception`() {
        val monday = date(2026, 10, 19)
        val now = at(2026, 10, 16, 8)
        assertEquals(OffReason.SKIPPED, SchedulingEngine.nextSuppression(alarm, RuleSet(skips = listOf(SkipNext(1, monday))), now)?.reason)
        assertEquals(OffReason.VACATION, SchedulingEngine.nextSuppression(alarm, RuleSet(vacations = listOf(Vacation(1, monday, monday))), now)?.reason)
        val off = RuleSet(overrides = listOf(DayOverride(1, monday, OverrideMode.FORCE_OFF)))
        assertEquals(Suppression(monday, OffReason.EXCEPTION), SchedulingEngine.nextSuppression(alarm, off, now))
    }

    @Test
    fun `today counts only while its time is ahead`() {
        val friday = date(2026, 10, 16)
        val rules = RuleSet(skips = listOf(SkipNext(1, friday)))
        assertEquals(friday, SchedulingEngine.nextSuppression(alarm, rules, at(2026, 10, 16, 6))?.date)
        assertNull(SchedulingEngine.nextSuppression(alarm, rules, at(2026, 10, 16, 8)))
    }

    @Test
    fun `suppression when the alarm never rings again`() {
        val rules = RuleSet(vacations = listOf(Vacation(1, date(2026, 10, 16), date(2030, 1, 1))))
        assertEquals(OffReason.VACATION, SchedulingEngine.nextSuppression(alarm, rules, at(2026, 10, 16, 6))?.reason)
        assertNull(SchedulingEngine.nextSuppression(weekly(days = emptySet()), RuleSet(), at(2026, 10, 16, 6)))
    }

    @Test
    fun `skip target is the next occurrence, even after the weekend`() {
        assertEquals(date(2026, 10, 19), SchedulingEngine.skipTarget(alarm, RuleSet(), at(2026, 10, 16, 8)))
        assertEquals(date(2026, 10, 16), SchedulingEngine.skipTarget(alarm, RuleSet(), at(2026, 10, 16, 6)))
        assertNull(SchedulingEngine.skipTarget(weekly(days = emptySet()), RuleSet(), at(2026, 10, 16, 6)))
    }

    @Test
    fun `skipping moves the next occurrence and undo restores it`() {
        val now = at(2026, 10, 16, 8)
        val target = SchedulingEngine.skipTarget(alarm, RuleSet(), now)!!
        val skipped = RuleSet(skips = listOf(SkipNext(1, target)))
        assertEquals(date(2026, 10, 20), next(alarm, now, skipped)?.date)
        assertEquals(date(2026, 10, 19), next(alarm, now, RuleSet())?.date)
    }

    @Test
    fun `skips are consumed once their time passes`() {
        val skips = listOf(SkipNext(1, date(2026, 10, 16)), SkipNext(1, date(2026, 10, 19)), SkipNext(99, date(2026, 10, 19)))
        assertEquals(listOf(SkipNext(1, date(2026, 10, 19))), SchedulingEngine.liveSkips(listOf(alarm), skips, at(2026, 10, 16, 7, 1)))
        assertEquals(skips.take(2), SchedulingEngine.liveSkips(listOf(alarm), skips, at(2026, 10, 16, 6, 59)))
    }

    @Test
    fun `holiday eve lists the affected alarms`() {
        val monday = date(2026, 10, 19)
        val rules = RuleSet(holidays = holidays(monday to "Fiesta"))
        val alarms = listOf(
            alarm,
            weekly(id = 2, respectHolidays = false),
            weekly(id = 3, days = setOf(java.time.DayOfWeek.SATURDAY)),
            weekly(id = 4).copy(enabled = false),
            AlarmSpec(5, java.time.LocalTime.of(9, 0), kind = AlarmKind.ONE_SHOT, oneShotDate = monday, respectHolidays = true),
            weekly(id = 6)
        )
        assertEquals(HolidayEve(Holiday(monday, "Fiesta"), listOf(1L, 6L)), SchedulingEngine.holidayEve(alarms, rules, monday))
    }

    @Test
    fun `holiday eve ignores answers already given and alarms silenced by other rules`() {
        val monday = date(2026, 10, 19)
        val rules = RuleSet(
            holidays = holidays(monday to "Fiesta"),
            decisions = listOf(HolidayDecision(monday, null, true)),
            skips = listOf(SkipNext(2, monday))
        )
        assertEquals(listOf(1L), SchedulingEngine.holidayEve(listOf(alarm, weekly(id = 2)), rules, monday)?.alarmIds)
    }

    @Test
    fun `no holiday eve without holiday or without alarms`() {
        val monday = date(2026, 10, 19)
        assertNull(SchedulingEngine.holidayEve(listOf(alarm), RuleSet(), monday))
        assertNull(SchedulingEngine.holidayEve(listOf(weekly(respectHolidays = false)), RuleSet(holidays = holidays(monday to "x")), monday))
    }

    @Test
    fun `effective holidays merge sources and overrides`() {
        val d1 = date(2026, 1, 6)
        val d2 = date(2026, 5, 15)
        val d3 = date(2026, 12, 7)
        val cal = EffectiveHolidays(
            api = listOf(Holiday(d1, "Reyes")),
            manual = listOf(Holiday(d1, "Mis Reyes"), Holiday(d3, "Puente")),
            yearly = listOf(YearlyHoliday(MonthDay.of(5, 15), "San Isidro")),
            overrides = listOf(HolidayOverride(d3, isHoliday = false), HolidayOverride(date(2026, 8, 1), isHoliday = true, name = "Mío"))
        )
        assertEquals("Mis Reyes", cal.holidayOn(d1)?.name)
        assertEquals("San Isidro", cal.holidayOn(d2)?.name)
        assertEquals("San Isidro", cal.holidayOn(date(2031, 5, 15))?.name)
        assertNull(cal.holidayOn(d3))
        assertEquals("Mío", cal.holidayOn(date(2026, 8, 1))?.name)
        assertNull(cal.holidayOn(date(2026, 8, 2)))
        assertNull(HolidayCalendar.NONE.holidayOn(d1))
    }

    @Test
    fun `override adding an existing holiday keeps its name`() {
        val d = date(2026, 1, 6)
        val cal = EffectiveHolidays(api = listOf(Holiday(d, "Reyes")), overrides = listOf(HolidayOverride(d, isHoliday = true, name = "x")))
        assertEquals("Reyes", cal.holidayOn(d)?.name)
        val yearlyCal = EffectiveHolidays(yearly = listOf(YearlyHoliday(MonthDay.of(1, 6), "Y")), overrides = listOf(HolidayOverride(d, true, "x")))
        assertEquals("Y", yearlyCal.holidayOn(d)?.name)
    }

    @Test
    fun `yearly holiday on 29 February only in leap years`() {
        val cal = EffectiveHolidays(yearly = listOf(YearlyHoliday(MonthDay.of(2, 29), "Bisiesto")))
        assertEquals("Bisiesto", cal.holidayOn(date(2028, 2, 29))?.name)
        assertNull(cal.holidayOn(date(2027, 2, 28)))
        assertNull(cal.holidayOn(date(2027, 3, 1)))
    }
}
