package com.qtekfun.ultimatealarm.feature.ringing

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import com.qtekfun.ultimatealarm.core.scheduling.atLocalTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * What a re-schedule must do, computed without touching the system so it can be unit-tested:
 * [register] the next occurrence of every alarm that has one (one entry per alarm, §4), [cancel] what was registered
 * before and no longer rings, flag one-shot alarms whose time passed without ringing as [missed] (RF-P5) and drop skips
 * whose occurrence is over ([staleSkips]).
 */
data class SchedulePlan(
    val register: Map<Long, Long>,
    val cancel: Set<Long>,
    val missed: List<Long>,
    val staleSkips: List<Pair<Long, LocalDate>>,
    /** RF-R6: alarm id → when to show "Alarm in 1 h", for which occurrence. */
    val preNotices: Map<Long, PreNotice> = emptyMap(),
    /** RF-F7: the next evening notice before a holiday on which some alarm would ring, or `null`. */
    val eve: EveNotice? = null,
    /** RF-E1: when to remind the user to go to bed, and the alarm it is for. */
    val bedtime: Bedtime? = null
)

data class Bedtime(val at: Long, val alarmAt: Long)

data class EveNotice(val at: Long, val holidayDate: LocalDate)

data class PreNotice(val at: Long, val occurrenceDate: LocalDate, val occurrenceAt: Long)

fun planSchedule(
    alarms: List<Alarm>,
    rules: RuleSet,
    now: ZonedDateTime,
    previouslyRegistered: Set<Long>,
    eveTime: LocalTime? = null,
    bedtimeHours: Int? = null
): SchedulePlan {
    val occurrences = alarms.mapNotNull { alarm -> SchedulingEngine.nextOccurrence(alarm.spec, rules, now)?.let { alarm to it } }
    val register = occurrences.associate { (alarm, occurrence) -> alarm.id to occurrence.at.toInstant().toEpochMilli() }
    val nowMillis = now.toInstant().toEpochMilli()
    val preNotices = occurrences.mapNotNull { (alarm, occurrence) ->
        val minutes = alarm.preNotifyMinutes ?: return@mapNotNull null
        val at = occurrence.at.minusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        if (at <= nowMillis) null else alarm.id to PreNotice(at, occurrence.date, occurrence.at.toInstant().toEpochMilli())
    }.toMap()
    val missed = alarms.filter { alarm ->
        alarm.kind == AlarmKind.ONE_SHOT && alarm.enabled && !alarm.missed && alarm.oneShotDate != null &&
            alarm.id !in register && alarm.lastFiredDate != alarm.oneShotDate
    }.map { it.id }
    val live = SchedulingEngine.liveSkips(alarms.map { it.spec }, rules.skips, now).toSet()
    val stale = rules.skips.filterNot { it in live }.map { it.alarmId to it.occurrenceDate }
    return SchedulePlan(
        register,
        previouslyRegistered - register.keys,
        missed,
        stale,
        preNotices,
        eveTime?.let { nextEve(alarms, rules, now, it) },
        bedtimeHours?.let { nextBedtime(alarms, rules, now, it) }
    )
}

/**
 * The first evening at [eveTime], still ahead, whose next day is a holiday with at least one alarm that respects holidays
 * and would ring if nobody answers (README assumption 2). Days already answered are skipped.
 */
private fun nextEve(alarms: List<Alarm>, rules: RuleSet, now: ZonedDateTime, eveTime: LocalTime): EveNotice? {
    val specs = alarms.map { it.spec }
    val today = now.toLocalDate()
    return (0..SchedulingEngine.HORIZON_DAYS).asSequence()
        .map { today.plusDays(it) }
        .map { evening -> evening.plusDays(1) to atLocalTime(evening, eveTime, now.zone) }
        .filter { (_, at) -> at.isAfter(now) }
        .filterNot { (holiday, _) -> rules.decisions.any { it.date == holiday && it.alarmId == null } }
        .firstOrNull { (holiday, _) -> SchedulingEngine.holidayEve(specs, rules, holiday) != null }
        ?.let { (holiday, at) -> EveNotice(at.toInstant().toEpochMilli(), holiday) }
}

/**
 * RF-E1: [hours] before the first alarm of a day, the earliest such reminder still ahead. Days with no alarm get no
 * reminder ("no sale si mañana no suena nada"). Looks a week ahead.
 */
private fun nextBedtime(alarms: List<Alarm>, rules: RuleSet, now: ZonedDateTime, hours: Int): Bedtime? {
    val specs = alarms.filter { it.enabled }.map { it.spec }
    return (0L..BEDTIME_DAYS).asSequence()
        .map { now.toLocalDate().plusDays(it) }
        .mapNotNull { day ->
            specs.filter { SchedulingEngine.explain(it, day, rules) == com.qtekfun.ultimatealarm.core.scheduling.DayVerdict.Rings }
                .map { atLocalTime(day, it.time, now.zone) }
                .filter { it.isAfter(now) }
                .minOrNull()
        }
        .map { first -> Bedtime(first.minusHours(hours.toLong()).toInstant().toEpochMilli(), first.toInstant().toEpochMilli()) }
        .firstOrNull { it.at > now.toInstant().toEpochMilli() }
}

private const val BEDTIME_DAYS = 7L
