package com.qtekfun.ultimatealarm.core.scheduling

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** DST in Spain (last Sunday of March and October), time zones and leap years. */
class EngineTimeTest {
    private val everyDay = DayOfWeek.entries.toSet()

    @Test
    fun `spring forward 2026 - 02 30 rings at 03 00 CEST`() {
        val alarm = weekly(h = 2, min = 30, days = everyDay)
        val occurrence = next(alarm, at(2026, 3, 28, 23))!!
        assertEquals(date(2026, 3, 29), occurrence.date)
        assertEquals(LocalTime.of(3, 0), occurrence.at.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), occurrence.at.offset)
    }

    @Test
    fun `spring forward does not change alarms outside the gap`() {
        val alarm = weekly(h = 7, days = everyDay)
        assertEquals(ZoneOffset.ofHours(2), next(alarm, at(2026, 3, 29, 1))!!.at.offset)
        assertEquals(LocalTime.of(7, 0), next(alarm, at(2026, 3, 29, 1))!!.at.toLocalTime())
    }

    @Test
    fun `fall back 2026 - 02 30 rings once, the first time`() {
        val alarm = weekly(h = 2, min = 30, days = everyDay)
        val first = next(alarm, at(2026, 10, 24, 23))!!
        assertEquals(ZoneOffset.ofHours(2), first.at.offset)
        // After the first 02:30 (CEST) the next one is the following day, not the repeated 02:30 CET.
        val afterFirst = first.at.plusMinutes(1)
        assertEquals(date(2026, 10, 26), next(alarm, afterFirst)!!.date)
    }

    @Test
    fun `fall back 2027 is the last Sunday of October`() {
        val alarm = weekly(h = 2, min = 30, days = everyDay)
        assertEquals(ZoneOffset.ofHours(2), next(alarm, at(2027, 10, 30, 23))!!.at.offset)
        assertEquals(ZoneOffset.ofHours(1), next(alarm, at(2027, 10, 31, 23))!!.at.offset)
    }

    @Test
    fun `wall clock follows the zone of now`() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val alarm = weekly(h = 7, days = everyDay)
        val occurrence = next(alarm, at(2026, 10, 16, 6, zone = lisbon))!!
        assertEquals(LocalTime.of(7, 0), occurrence.at.toLocalTime())
        assertEquals(lisbon, occurrence.at.zone)
    }

    @Test
    fun `travelling east the alarm may be later today`() {
        // 06:30 in Madrid = 07:30 in Athens: a 07:00 alarm already passed in Athens.
        val athens = ZoneId.of("Europe/Athens")
        val now = at(2026, 10, 16, 6, 30).withZoneSameInstant(athens)
        assertEquals(date(2026, 10, 19), next(weekly(h = 7), now)!!.date)
    }

    @Test
    fun `leap day 2028 is a normal Tuesday`() {
        val alarm = weekly(h = 7, days = setOf(DayOfWeek.TUESDAY))
        assertEquals(date(2028, 2, 29), next(alarm, at(2028, 2, 28, 8))!!.date)
    }

    @Test
    fun `cycle across a leap day keeps counting days`() {
        val alarm = AlarmSpec(1, LocalTime.of(7, 0), schedule = Schedule.Cycle(date(2028, 2, 27), listOf(true, false, false)))
        // 27 on, 28 off, 29 off, 1 March on.
        assertEquals(date(2028, 3, 1), next(alarm, at(2028, 2, 27, 8))!!.date)
    }

    @Test
    fun `one-shot on a leap day`() {
        val alarm = AlarmSpec(1, LocalTime.of(7, 0), kind = AlarmKind.ONE_SHOT, oneShotDate = date(2028, 2, 29))
        assertEquals(at(2028, 2, 29, 7), next(alarm, at(2027, 12, 1, 0))!!.at)
    }

    @Test
    fun `new year crossing`() {
        assertEquals(date(2027, 1, 1), next(weekly(h = 7, days = setOf(DayOfWeek.FRIDAY)), at(2026, 12, 31, 8))!!.date)
    }
}
