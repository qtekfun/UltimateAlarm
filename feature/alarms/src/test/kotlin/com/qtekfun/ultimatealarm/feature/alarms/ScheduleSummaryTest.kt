package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSummaryTest {
    private fun weekly(vararg days: DayOfWeek) = Alarm(schedule = Schedule.Weekly(days.toSet()))

    @Test
    fun `weekly shortcuts`() {
        assertEquals(ScheduleSummary.EveryDay, summarize(weekly(*DayOfWeek.entries.toTypedArray())))
        assertEquals(ScheduleSummary.Weekdays, summarize(Alarm()))
        assertEquals(ScheduleSummary.Weekend, summarize(weekly(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)))
        assertEquals(ScheduleSummary.NoDays, summarize(weekly()))
    }

    @Test
    fun `other day sets are listed Monday first`() {
        assertEquals(
            ScheduleSummary.Days(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)),
            summarize(weekly(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        )
    }

    @Test
    fun `cycles and one-shot alarms`() {
        assertEquals(ScheduleSummary.Cycle(8, 4), summarize(Alarm(schedule = Schedule.Cycle(LocalDate.of(2026, 1, 1), List(4) { true } + List(4) { false }))))
        assertEquals(ScheduleSummary.Once(LocalDate.of(2026, 12, 24)), summarize(Alarm(kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.of(2026, 12, 24))))
        assertEquals(ScheduleSummary.NextTime, summarize(Alarm(kind = AlarmKind.ONE_SHOT)))
    }
}
