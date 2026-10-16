package com.qtekfun.ultimatealarm.core.scheduling

import org.junit.Assert.assertEquals
import org.junit.Test

/** The precedence of 02-diseno §3: override > vacation > skip > holiday decision > base pattern. */
class EnginePrecedenceTest {
    private val alarm = weekly()
    private val monday = date(2026, 10, 19)
    private val saturday = date(2026, 10, 17)

    private fun verdict(rules: RuleSet, day: java.time.LocalDate = monday) = SchedulingEngine.explain(alarm, day, rules)

    @Test
    fun `base pattern alone`() {
        assertEquals(DayVerdict.Rings, verdict(RuleSet()))
        assertEquals(DayVerdict.Off(OffReason.NOT_SCHEDULED), verdict(RuleSet(), saturday))
    }

    @Test
    fun `force on makes an unscheduled day ring`() {
        assertEquals(DayVerdict.Rings, verdict(RuleSet(overrides = listOf(DayOverride(1, saturday, OverrideMode.FORCE_ON))), saturday))
    }

    @Test
    fun `force off silences a scheduled day`() {
        assertEquals(DayVerdict.Off(OffReason.EXCEPTION), verdict(RuleSet(overrides = listOf(DayOverride(1, monday, OverrideMode.FORCE_OFF)))))
    }

    @Test
    fun `override of another alarm does not apply`() {
        assertEquals(DayVerdict.Rings, verdict(RuleSet(overrides = listOf(DayOverride(2, monday, OverrideMode.FORCE_OFF)))))
    }

    @Test
    fun `force on beats vacation, skip and holiday`() {
        val rules = RuleSet(
            overrides = listOf(DayOverride(1, monday, OverrideMode.FORCE_ON)),
            vacations = listOf(Vacation(1, monday, monday)),
            skips = listOf(SkipNext(1, monday)),
            holidays = holidays(monday to "Fiesta"),
            decisions = listOf(HolidayDecision(monday, null, skip = true))
        )
        assertEquals(DayVerdict.Rings, verdict(rules))
    }

    @Test
    fun `vacation beats skip and holiday`() {
        val rules = RuleSet(
            vacations = listOf(Vacation(1, monday, monday)),
            skips = listOf(SkipNext(1, monday)),
            holidays = holidays(monday to "Fiesta"),
            decisions = listOf(HolidayDecision(monday, null, skip = true))
        )
        assertEquals(DayVerdict.Off(OffReason.VACATION), verdict(rules))
    }

    @Test
    fun `skip beats holiday`() {
        val rules = RuleSet(
            skips = listOf(SkipNext(1, monday)),
            holidays = holidays(monday to "Fiesta"),
            decisions = listOf(HolidayDecision(monday, null, skip = true))
        )
        assertEquals(DayVerdict.Off(OffReason.SKIPPED), verdict(rules))
    }

    @Test
    fun `vacation for all alarms and for a selection`() {
        assertEquals(DayVerdict.Off(OffReason.VACATION), verdict(RuleSet(vacations = listOf(Vacation(1, monday, monday)))))
        assertEquals(DayVerdict.Off(OffReason.VACATION), verdict(RuleSet(vacations = listOf(Vacation(1, monday, monday, setOf(1L))))))
        assertEquals(DayVerdict.Rings, verdict(RuleSet(vacations = listOf(Vacation(1, monday, monday, setOf(2L))))))
    }

    @Test
    fun `vacation bounds are inclusive`() {
        val rules = RuleSet(vacations = listOf(Vacation(1, date(2026, 10, 19), date(2026, 10, 23))))
        assertEquals(DayVerdict.Off(OffReason.VACATION), verdict(rules, date(2026, 10, 19)))
        assertEquals(DayVerdict.Off(OffReason.VACATION), verdict(rules, date(2026, 10, 23)))
        assertEquals(DayVerdict.Rings, verdict(rules, date(2026, 10, 26)))
        assertEquals(at(2026, 10, 26, 7), next(alarm, at(2026, 10, 16, 8), rules)?.at)
    }

    @Test
    fun `skip only affects its own alarm and date`() {
        val rules = RuleSet(skips = listOf(SkipNext(2, monday), SkipNext(1, date(2026, 10, 20))))
        assertEquals(DayVerdict.Rings, verdict(rules))
    }

    @Test
    fun `the last override for a day wins`() {
        val rules = RuleSet(overrides = listOf(DayOverride(1, monday, OverrideMode.FORCE_OFF), DayOverride(1, monday, OverrideMode.FORCE_ON)))
        assertEquals(DayVerdict.Rings, verdict(rules))
    }
}
