package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZonedDateTime

/** What the engine needs to know about an alarm (02-diseno §2). Sound, snooze and the rest live in the data layer. */
data class AlarmSpec(
    val id: Long,
    val time: LocalTime,
    val enabled: Boolean = true,
    val kind: AlarmKind = AlarmKind.RECURRING,
    /** Recurring: the base pattern. Ignored for one-shot alarms. */
    val schedule: Schedule = Schedule.Weekly(emptySet()),
    /** One-shot: the day it rings. `null` means "the next time it is [time]". */
    val oneShotDate: LocalDate? = null,
    val respectHolidays: Boolean = kind == AlarmKind.RECURRING
)

enum class AlarmKind { RECURRING, ONE_SHOT }

enum class OverrideMode { FORCE_ON, FORCE_OFF }

/** Per-day exception of one alarm (RF-P3). */
data class DayOverride(val alarmId: Long, val date: LocalDate, val mode: OverrideMode)

/** "Skip tomorrow" (RF-P6): the occurrence of [alarmId] on [occurrenceDate] does not ring. */
data class SkipNext(val alarmId: Long, val occurrenceDate: LocalDate)

/** A vacation from [start] to [end], both inclusive (RF-P4). `alarmIds == null` means every alarm. */
data class Vacation(val id: Long, val start: LocalDate, val end: LocalDate, val alarmIds: Set<Long>? = null) {
    fun covers(date: LocalDate, alarmId: Long): Boolean = !date.isBefore(start) && !date.isAfter(end) && (alarmIds == null || alarmId in alarmIds)
}

/** The user's answer the evening before a holiday (RF-F7). `alarmId == null` answers for every alarm on that date. */
data class HolidayDecision(val date: LocalDate, val alarmId: Long?, val skip: Boolean)

/** Everything besides the alarm itself that decides whether it rings on a day. */
data class RuleSet(
    val overrides: List<DayOverride> = emptyList(),
    val vacations: List<Vacation> = emptyList(),
    val skips: List<SkipNext> = emptyList(),
    val holidays: HolidayCalendar = HolidayCalendar.NONE,
    val decisions: List<HolidayDecision> = emptyList()
) {
    internal fun overrideFor(alarmId: Long, date: LocalDate): OverrideMode? = overrides.lastOrNull { it.alarmId == alarmId && it.date == date }?.mode

    internal fun vacationCovers(alarmId: Long, date: LocalDate): Boolean = vacations.any { it.covers(date, alarmId) }

    internal fun isSkipped(alarmId: Long, date: LocalDate): Boolean = skips.any { it.alarmId == alarmId && it.occurrenceDate == date }

    /** The alarm's own decision wins over a decision for every alarm. `null`: nobody answered. */
    internal fun decisionFor(alarmId: Long, date: LocalDate): Boolean? = decisions.lastOrNull { it.date == date && it.alarmId == alarmId }?.skip
        ?: decisions.lastOrNull { it.date == date && it.alarmId == null }?.skip
}

/** A day the alarm rings, at an exact instant. */
data class Occurrence(val at: ZonedDateTime, val date: LocalDate)

enum class OffReason { EXCEPTION, VACATION, SKIPPED, HOLIDAY, NOT_SCHEDULED, DISABLED }

/** Whether an alarm rings on a day and, if not, why (RF-A2, RF-P7). */
sealed interface DayVerdict {
    data object Rings : DayVerdict

    data class Off(val reason: OffReason) : DayVerdict
}

data class Holiday(val date: LocalDate, val name: String)

/** A day the base pattern would ring on but a rule silences, shown on the alarm row (RF-A2). */
data class Suppression(val date: LocalDate, val reason: OffReason, val holiday: Holiday? = null)

/** Recurring manual holiday that repeats every year on [monthDay] (RF-F4). */
data class YearlyHoliday(val monthDay: MonthDay, val name: String)
