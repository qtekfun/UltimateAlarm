package com.qtekfun.ultimatealarm.feature.ringing

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulePlanTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 10, 16, 8, 0, 0, 0, zone) // Friday
    private val weekly = Alarm(id = 1, time = LocalTime.of(7, 0))

    private fun millis(y: Int, m: Int, d: Int, h: Int) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `registers the next occurrence of every ringing alarm`() {
        val oneShot = Alarm(id = 2, time = LocalTime.of(9, 0), kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.of(2026, 10, 20))
        val plan = planSchedule(listOf(weekly, oneShot, weekly.copy(id = 3, enabled = false)), RuleSet(), now, emptySet())
        assertEquals(mapOf(1L to millis(2026, 10, 19, 7), 2L to millis(2026, 10, 20, 9)), plan.register)
    }

    @Test
    fun `cancels what was registered and no longer rings`() {
        val plan = planSchedule(listOf(weekly, weekly.copy(id = 3, enabled = false)), RuleSet(), now, setOf(1L, 3L, 4L))
        assertEquals(setOf(3L, 4L), plan.cancel)
    }

    @Test
    fun `one-shot past without firing is missed, fired or already flagged is not`() {
        val day = LocalDate.of(2026, 10, 16)
        val lost = Alarm(id = 5, time = LocalTime.of(7, 30), kind = AlarmKind.ONE_SHOT, oneShotDate = day)
        val rang = lost.copy(id = 6, lastFiredDate = day)
        val flagged = lost.copy(id = 7, missed = true)
        val off = lost.copy(id = 8, enabled = false)
        assertEquals(listOf(5L), planSchedule(listOf(lost, rang, flagged, off), RuleSet(), now, emptySet()).missed)
    }

    @Test
    fun `stale skips are dropped`() {
        val rules = RuleSet(skips = listOf(SkipNext(1, LocalDate.of(2026, 10, 16)), SkipNext(1, LocalDate.of(2026, 10, 19))))
        assertEquals(listOf(1L to LocalDate.of(2026, 10, 16)), planSchedule(listOf(weekly), rules, now, emptySet()).staleSkips)
    }
}
