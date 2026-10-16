package com.qtekfun.ultimatealarm.core.data.db

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.DayOverride
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `weekly alarm round trips`() {
        val alarm = Alarm(
            id = 3,
            time = LocalTime.of(6, 45),
            label = "Trabajo",
            schedule = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            sound = "bundled:dawn",
            backupSound = "system:x",
            maxVolume = 80,
            rampSeconds = 60,
            vibrate = false,
            snoozeMinutes = 5,
            maxSnoozes = Alarm.UNLIMITED_SNOOZES,
            backupAfterMinutes = null,
            challenge = "math:2:3",
            preNotifyMinutes = 60,
            missed = true,
            lastFiredDate = LocalDate.of(2026, 10, 15)
        )
        assertEquals(0b1000001, alarm.toEntity().weekDays)
        assertEquals(alarm, alarm.toEntity().toAlarm())
    }

    @Test
    fun `cycle and one-shot round trip`() {
        val cycle = Alarm(id = 1, schedule = Schedule.Cycle(LocalDate.of(2026, 10, 1), listOf(true, true, false, false)))
        assertEquals("1100", cycle.toEntity().cycleMask)
        assertEquals(cycle, cycle.toEntity().toAlarm())
        val oneShot = Alarm(id = 2, kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.of(2026, 12, 24), respectHolidays = false)
        assertEquals(oneShot, oneShot.toEntity().toAlarm())
    }

    @Test
    fun `broken cycle falls back to weekly and unknown kind to recurring`() {
        val entity = Alarm(id = 1, schedule = Schedule.Cycle(LocalDate.of(2026, 10, 1), listOf(true))).toEntity()
            .copy(cycleMask = "", kind = "SOMETHING")
        val alarm = entity.toAlarm()
        assertEquals(Schedule.Weekly(emptySet()), alarm.schedule)
        assertEquals(AlarmKind.RECURRING, alarm.kind)
    }

    @Test
    fun `spec carries what the engine needs`() {
        val alarm = Alarm(id = 7, time = LocalTime.of(8, 0), enabled = false, respectHolidays = false)
        val spec = alarm.spec
        assertEquals(7L, spec.id)
        assertEquals(LocalTime.of(8, 0), spec.time)
        assertEquals(false, spec.enabled)
        assertEquals(false, spec.respectHolidays)
        assertEquals(alarm.schedule, spec.schedule)
    }

    @Test
    fun `rules map from rows`() {
        val day = LocalDate.of(2026, 10, 19)
        assertEquals(DayOverride(1, day, OverrideMode.FORCE_OFF), DayOverrideEntity(1, day.toEpochDay(), "FORCE_OFF").toRule())
        assertNull(DayOverrideEntity(1, day.toEpochDay(), "???").toRule())
        assertEquals(SkipNext(1, day), SkipEntity(1, day.toEpochDay()).toRule())
        assertEquals(HolidayDecision(day, null, true), HolidayDecisionEntity(day.toEpochDay(), HolidayDecisionEntity.ALL_ALARMS, true).toRule())
        assertEquals(HolidayDecision(day, 4, false), HolidayDecisionEntity(day.toEpochDay(), 4, false).toRule())
    }

    @Test
    fun `vacations for all alarms and for a selection`() {
        val start = LocalDate.of(2026, 8, 1)
        val end = LocalDate.of(2026, 8, 15)
        val rows = listOf(VacationEntity(1, start.toEpochDay(), end.toEpochDay(), true), VacationEntity(2, start.toEpochDay(), end.toEpochDay(), false))
        val members = listOf(VacationAlarmEntity(2, 5), VacationAlarmEntity(2, 6))
        assertEquals(listOf(Vacation(1, start, end, null), Vacation(2, start, end, setOf(5L, 6L))), vacationRules(rows, members))
    }
}
