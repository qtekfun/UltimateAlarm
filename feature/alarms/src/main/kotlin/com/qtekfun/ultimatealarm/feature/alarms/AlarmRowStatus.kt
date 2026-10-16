package com.qtekfun.ultimatealarm.feature.alarms

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Occurrence
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import com.qtekfun.ultimatealarm.core.scheduling.Suppression
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** What an alarm row shows (RF-A2): when it rings next and, if a rule silences a day before that, which and why. */
data class AlarmRow(
    val alarm: Alarm,
    val next: Occurrence?,
    val suppression: Suppression?,
    /** The date "skip tomorrow" acts on, or `null` when the button does not apply (one-shot, never rings). */
    val skipTarget: LocalDate?,
    /** A pending skip of this alarm, which the button offers to undo (RF-P6). */
    val activeSkip: LocalDate?
) {
    fun ringsIn(now: ZonedDateTime): Duration? = next?.let { Duration.between(now, it.at) }
}

/** Rows sorted by next occurrence; alarms that never ring go last, by time (RF-A2). */
fun alarmRows(alarms: List<Alarm>, rules: RuleSet, now: ZonedDateTime): List<AlarmRow> = alarms.map { alarm ->
    val spec = alarm.spec
    val activeSkip = rules.skips.filter { it.alarmId == alarm.id }.map { it.occurrenceDate }.filter { !it.isBefore(now.toLocalDate()) }.minOrNull()
    AlarmRow(
        alarm = alarm,
        next = SchedulingEngine.nextOccurrence(spec, rules, now),
        suppression = SchedulingEngine.nextSuppression(spec, rules, now),
        skipTarget = if (alarm.kind == AlarmKind.ONE_SHOT || activeSkip != null) null else SchedulingEngine.skipTarget(spec, rules, now),
        activeSkip = activeSkip
    )
}.sortedWith(compareBy<AlarmRow, Long?>(nullsLast()) { it.next?.at?.toEpochSecond() }.thenBy { it.alarm.time }.thenBy { it.alarm.id })

/** "Suena en 5 h 12 min" rounds up to the next minute, as alarm clocks do: 30 s left is "1 min". */
fun hoursAndMinutes(duration: Duration): Pair<Long, Long> {
    val minutes = (duration.seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    return minutes / MINUTES_PER_HOUR to minutes % MINUTES_PER_HOUR
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
