package com.qtekfun.ultimatealarm.core.scheduling

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The scheduling engine (02-diseno §3). Pure: same inputs, same answer; no clock, no Android.
 *
 * For a recurring alarm each candidate day `d` from today on (at most [HORIZON_DAYS]) is decided by the first rule that
 * applies: (1) day override, (2) vacation, (3) skip, (4) holiday with a "skip" decision (no decision: it rings),
 * (5) the base pattern. The instant on `d` follows the DST rules of [atLocalTime].
 *
 * One-shot alarms ring on their date at their time if that is still ahead and ignore every rule: the user turns them
 * off with their switch instead (docs/spec/DECISIONS.md D-11).
 */
object SchedulingEngine {
    const val HORIZON_DAYS = 400L

    fun nextOccurrence(alarm: AlarmSpec, rules: RuleSet, now: ZonedDateTime): Occurrence? {
        if (!alarm.enabled) return null
        val zone = now.zone
        if (alarm.kind == AlarmKind.ONE_SHOT) {
            val date = alarm.oneShotDate ?: return nextDailyOccurrence(alarm.time, now).let { Occurrence(it, it.toLocalDate()) }
            val at = atLocalTime(date, alarm.time, zone)
            return if (at.isAfter(now)) Occurrence(at, date) else null
        }
        val today = now.toLocalDate()
        for (offset in 0..HORIZON_DAYS) {
            val date = today.plusDays(offset)
            if (explain(alarm, date, rules) != DayVerdict.Rings) continue
            val at = atLocalTime(date, alarm.time, zone)
            if (at.isAfter(now)) return Occurrence(at, date)
        }
        return null
    }

    fun explain(alarm: AlarmSpec, date: LocalDate, rules: RuleSet): DayVerdict = when {
        !alarm.enabled -> DayVerdict.Off(OffReason.DISABLED)
        alarm.kind == AlarmKind.ONE_SHOT -> explainOneShot(alarm, date)
        else -> explainRecurring(alarm, date, rules)
    }

    private fun explainOneShot(alarm: AlarmSpec, date: LocalDate): DayVerdict =
        if (alarm.oneShotDate == null || alarm.oneShotDate == date) DayVerdict.Rings else DayVerdict.Off(OffReason.NOT_SCHEDULED)

    private fun explainRecurring(alarm: AlarmSpec, date: LocalDate, rules: RuleSet): DayVerdict {
        val override = rules.overrideFor(alarm.id, date)
        return when {
            override == OverrideMode.FORCE_ON -> DayVerdict.Rings
            override == OverrideMode.FORCE_OFF -> DayVerdict.Off(OffReason.EXCEPTION)
            rules.vacationCovers(alarm.id, date) -> DayVerdict.Off(OffReason.VACATION)
            rules.isSkipped(alarm.id, date) -> DayVerdict.Off(OffReason.SKIPPED)
            alarm.respectHolidays && rules.holidays.holidayOn(date) != null && rules.decisionFor(alarm.id, date) == true -> DayVerdict.Off(OffReason.HOLIDAY)
            alarm.schedule.isScheduledOn(date) -> DayVerdict.Rings
            else -> DayVerdict.Off(OffReason.NOT_SCHEDULED)
        }
    }

    /**
     * The first day before the next occurrence on which the base pattern would ring but a rule silences the alarm, with
     * the reason (RF-A2: "no sonará mañana: festivo"). Only days whose time is still ahead count. `null` when nothing is
     * suppressed before the next occurrence.
     */
    fun nextSuppression(alarm: AlarmSpec, rules: RuleSet, now: ZonedDateTime): Suppression? {
        if (!alarm.enabled || alarm.kind == AlarmKind.ONE_SHOT) return null
        val next = nextOccurrence(alarm, rules, now)
        val today = now.toLocalDate()
        for (offset in 0..HORIZON_DAYS) {
            val date = today.plusDays(offset)
            if (next != null && !date.isBefore(next.date)) return null
            if (!atLocalTime(date, alarm.time, now.zone).isAfter(now)) continue
            val verdict = explain(alarm, date, rules)
            val patternRings = alarm.schedule.isScheduledOn(date)
            if (verdict is DayVerdict.Off && verdict.reason != OffReason.NOT_SCHEDULED && patternRings) {
                return Suppression(date, verdict.reason, rules.holidays.holidayOn(date).takeIf { verdict.reason == OffReason.HOLIDAY })
            }
        }
        return null
    }

    /**
     * The date "skip tomorrow" acts on (RF-P6, README assumption 3): the next occurrence, whatever day it is. `null` for
     * one-shot alarms (they use their switch) and alarms that never ring.
     */
    fun skipTarget(alarm: AlarmSpec, rules: RuleSet, now: ZonedDateTime): LocalDate? =
        if (alarm.kind == AlarmKind.ONE_SHOT) null else nextOccurrence(alarm, rules, now)?.date

    /**
     * Skips whose day has passed (their time is behind [now]) can be deleted: "consumed when it passes" (§2). Returns
     * the skips to keep.
     */
    fun liveSkips(alarms: List<AlarmSpec>, skips: List<SkipNext>, now: ZonedDateTime): List<SkipNext> {
        val byId = alarms.associateBy { it.id }
        return skips.filter { skip ->
            val alarm = byId[skip.alarmId] ?: return@filter false
            atLocalTime(skip.occurrenceDate, alarm.time, now.zone).isAfter(now)
        }
    }

    /**
     * Holiday eve (RF-F7): if [tomorrow] is an effective holiday, the alarms that respect holidays and would ring that day
     * if nobody answered. Empty when tomorrow is not a holiday or no such alarm rings (README assumption 2).
     */
    fun holidayEve(alarms: List<AlarmSpec>, rules: RuleSet, tomorrow: LocalDate): HolidayEve? {
        val holiday = rules.holidays.holidayOn(tomorrow) ?: return null
        val undecided = rules.copy(decisions = rules.decisions.filterNot { it.date == tomorrow })
        val affected = alarms.filter { it.kind == AlarmKind.RECURRING && it.respectHolidays && explain(it, tomorrow, undecided) == DayVerdict.Rings }
        return if (affected.isEmpty()) null else HolidayEve(holiday, affected.map { it.id })
    }
}

data class HolidayEve(val holiday: Holiday, val alarmIds: List<Long>)
