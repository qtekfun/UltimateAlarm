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

internal fun Alarm.toEntity(): AlarmEntity {
    val weekly = schedule as? Schedule.Weekly
    val cycle = schedule as? Schedule.Cycle
    return AlarmEntity(
        id = id,
        timeOfDay = time.toSecondOfDay(),
        label = label,
        enabled = enabled,
        kind = kind.name,
        scheduleType = if (cycle != null) TYPE_CYCLE else TYPE_WEEKLY,
        weekDays = weekly?.days?.fold(0) { bits, day -> bits or (1 shl (day.value - 1)) } ?: 0,
        cycleAnchor = cycle?.anchor?.toEpochDay(),
        cycleMask = cycle?.mask?.joinToString("") { if (it) "1" else "0" },
        oneShotDate = oneShotDate?.toEpochDay(),
        respectHolidays = respectHolidays,
        sound = sound,
        backupSound = backupSound,
        maxVolume = maxVolume,
        rampSeconds = rampSeconds,
        vibrate = vibrate,
        snoozeMinutes = snoozeMinutes,
        maxSnoozes = maxSnoozes,
        backupAfterMinutes = backupAfterMinutes,
        challenge = challenge,
        preNotifyMinutes = preNotifyMinutes,
        missed = missed,
        lastFiredDate = lastFiredDate?.toEpochDay()
    )
}

internal fun AlarmEntity.toAlarm(): Alarm {
    val anchor = cycleAnchor
    val mask = cycleMask
    val schedule = if (scheduleType == TYPE_CYCLE && anchor != null && !mask.isNullOrEmpty()) {
        Schedule.Cycle(LocalDate.ofEpochDay(anchor), mask.map { it == '1' })
    } else {
        Schedule.Weekly(DayOfWeek.entries.filter { weekDays and (1 shl (it.value - 1)) != 0 }.toSet())
    }
    return Alarm(
        id = id,
        time = LocalTime.ofSecondOfDay(timeOfDay.toLong()),
        label = label,
        enabled = enabled,
        kind = enumValueOrNull<AlarmKind>(kind) ?: AlarmKind.RECURRING,
        schedule = schedule,
        oneShotDate = oneShotDate?.let(LocalDate::ofEpochDay),
        respectHolidays = respectHolidays,
        sound = sound,
        backupSound = backupSound,
        maxVolume = maxVolume,
        rampSeconds = rampSeconds,
        vibrate = vibrate,
        snoozeMinutes = snoozeMinutes,
        maxSnoozes = maxSnoozes,
        backupAfterMinutes = backupAfterMinutes,
        challenge = challenge,
        preNotifyMinutes = preNotifyMinutes,
        missed = missed,
        lastFiredDate = lastFiredDate?.let(LocalDate::ofEpochDay)
    )
}

internal fun DayOverrideEntity.toRule(): DayOverride? = enumValueOrNull<OverrideMode>(mode)?.let { DayOverride(alarmId, LocalDate.ofEpochDay(date), it) }

internal fun SkipEntity.toRule() = SkipNext(alarmId, LocalDate.ofEpochDay(date))

internal fun vacationRules(vacations: List<VacationEntity>, members: List<VacationAlarmEntity>): List<Vacation> {
    val byVacation = members.groupBy({ it.vacationId }, { it.alarmId })
    return vacations.map { v ->
        Vacation(v.id, LocalDate.ofEpochDay(v.start), LocalDate.ofEpochDay(v.end), if (v.allAlarms) null else byVacation[v.id].orEmpty().toSet())
    }
}

internal fun HolidayDecisionEntity.toRule() = HolidayDecision(LocalDate.ofEpochDay(date), alarmId.takeUnless { it == HolidayDecisionEntity.ALL_ALARMS }, skip)

private inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

private const val TYPE_WEEKLY = "WEEKLY"
private const val TYPE_CYCLE = "CYCLE"
