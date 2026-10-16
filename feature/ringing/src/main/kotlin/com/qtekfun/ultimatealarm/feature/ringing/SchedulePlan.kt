package com.qtekfun.ultimatealarm.feature.ringing

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import java.time.LocalDate
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
    val preNotices: Map<Long, PreNotice> = emptyMap()
)

data class PreNotice(val at: Long, val occurrenceDate: LocalDate, val occurrenceAt: Long)

fun planSchedule(alarms: List<Alarm>, rules: RuleSet, now: ZonedDateTime, previouslyRegistered: Set<Long>): SchedulePlan {
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
    return SchedulePlan(register, previouslyRegistered - register.keys, missed, stale, preNotices)
}
