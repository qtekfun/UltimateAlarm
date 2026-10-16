package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.LocalDate

/** How a row summarises when an alarm repeats; turned into localized text by the UI. */
sealed interface ScheduleSummary {
    data object EveryDay : ScheduleSummary

    data object Weekdays : ScheduleSummary

    data object Weekend : ScheduleSummary

    data object NoDays : ScheduleSummary

    /** Days in week order, Monday first. */
    data class Days(val days: List<DayOfWeek>) : ScheduleSummary

    data class Cycle(val length: Int, val ringing: Int) : ScheduleSummary

    data class Once(val date: LocalDate) : ScheduleSummary

    data object NextTime : ScheduleSummary
}

val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun summarize(alarm: Alarm): ScheduleSummary {
    if (alarm.kind == AlarmKind.ONE_SHOT) return alarm.oneShotDate?.let { ScheduleSummary.Once(it) } ?: ScheduleSummary.NextTime
    return when (val schedule = alarm.schedule) {
        is Schedule.Cycle -> ScheduleSummary.Cycle(schedule.mask.size, schedule.mask.count { it })
        is Schedule.Weekly -> when (schedule.days) {
            DayOfWeek.entries.toSet() -> ScheduleSummary.EveryDay
            Alarm.WORKDAYS -> ScheduleSummary.Weekdays
            WEEKEND -> ScheduleSummary.Weekend
            emptySet<DayOfWeek>() -> ScheduleSummary.NoDays
            else -> ScheduleSummary.Days(schedule.days.sorted())
        }
    }
}
