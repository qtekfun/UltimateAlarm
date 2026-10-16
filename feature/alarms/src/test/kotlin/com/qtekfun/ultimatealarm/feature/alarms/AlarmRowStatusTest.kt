package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.OffReason
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRowStatusTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 10, 16, 8, 0, 0, 0, zone) // Friday 08:00

    @Test
    fun `rows are sorted by next occurrence and never-ringing alarms go last`() {
        val monday7 = Alarm(id = 1, time = LocalTime.of(7, 0))
        val today9 = Alarm(id = 2, time = LocalTime.of(9, 0))
        val off = Alarm(id = 3, time = LocalTime.of(6, 0), enabled = false)
        val tomorrow = Alarm(id = 4, time = LocalTime.of(10, 0), kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.of(2026, 10, 17))
        val rows = alarmRows(listOf(off, monday7, tomorrow, today9), RuleSet(), now)
        assertEquals(listOf(2L, 4L, 1L, 3L), rows.map { it.alarm.id })
    }

    @Test
    fun `skip target and active skip`() {
        val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))
        val row = alarmRows(listOf(alarm), RuleSet(), now).single()
        assertEquals(LocalDate.of(2026, 10, 19), row.skipTarget)
        assertNull(row.activeSkip)

        val skipped = alarmRows(listOf(alarm), RuleSet(skips = listOf(SkipNext(1, LocalDate.of(2026, 10, 19)))), now).single()
        assertNull(skipped.skipTarget)
        assertEquals(LocalDate.of(2026, 10, 19), skipped.activeSkip)
        assertEquals(LocalDate.of(2026, 10, 20), skipped.next?.date)
        assertEquals(OffReason.SKIPPED, skipped.suppression?.reason)
    }

    @Test
    fun `old skips are not active and one-shot alarms have no skip button`() {
        val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))
        val row = alarmRows(listOf(alarm), RuleSet(skips = listOf(SkipNext(1, LocalDate.of(2026, 10, 15)))), now).single()
        assertNull(row.activeSkip)
        val oneShot = Alarm(id = 2, kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.of(2026, 10, 20))
        assertNull(alarmRows(listOf(oneShot), RuleSet(), now).single().skipTarget)
    }

    @Test
    fun `rings in`() {
        val row = alarmRows(listOf(Alarm(id = 1, time = LocalTime.of(9, 30), schedule = Schedule.Weekly(Alarm.WORKDAYS))), RuleSet(), now).single()
        assertEquals(Duration.ofMinutes(90), row.ringsIn(now))
        assertNull(alarmRows(listOf(Alarm(id = 2, enabled = false)), RuleSet(), now).single().ringsIn(now))
    }

    @Test
    fun `hours and minutes round up`() {
        assertEquals(0L to 1L, hoursAndMinutes(Duration.ofSeconds(30)))
        assertEquals(1L to 30L, hoursAndMinutes(Duration.ofMinutes(90)))
        assertEquals(5L to 13L, hoursAndMinutes(Duration.ofMinutes(312).plusSeconds(1)))
        assertEquals(0L to 0L, hoursAndMinutes(Duration.ZERO))
    }
}
