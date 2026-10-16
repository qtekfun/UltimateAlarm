package com.qtekfun.ultimatealarm.core.data

import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.AlarmSpec
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** An alarm with every setting of RF-A1. [spec] is what the scheduling engine sees. */
data class Alarm(
    val id: Long = 0,
    val time: LocalTime = LocalTime.of(7, 0),
    val label: String = "",
    val enabled: Boolean = true,
    val kind: AlarmKind = AlarmKind.RECURRING,
    val schedule: Schedule = Schedule.Weekly(WORKDAYS),
    val oneShotDate: LocalDate? = null,
    val respectHolidays: Boolean = kind == AlarmKind.RECURRING,
    /** Serialized `SoundRef` (Phase 5); `null` = the default tone. */
    val sound: String? = null,
    /** Backup tone override (RF-S5); `null` = the global backup tone. */
    val backupSound: String? = null,
    /** 1–100 % of the alarm stream. */
    val maxVolume: Int = 100,
    /** Seconds from silence to [maxVolume] (RF-R2, 0–300). */
    val rampSeconds: Int = 30,
    val vibrate: Boolean = true,
    /** 1–30 minutes (RF-R3). */
    val snoozeMinutes: Int = 10,
    /** 0–10, or [UNLIMITED_SNOOZES]. */
    val maxSnoozes: Int = 3,
    /** Backup alarm after this many minutes without interaction (RF-R4); `null` disables it. */
    val backupAfterMinutes: Int? = 5,
    /** Serialized challenge (Phase 8); `null` = none. */
    val challenge: String? = null,
    /** "Alarm in 1 h" notification (RF-R6); `null` disables it. */
    val preNotifyMinutes: Int? = null,
    /** One-shot that could not ring (phone off): kept and flagged (RF-P5). */
    val missed: Boolean = false,
    /** Last day this alarm fired, to tell a missed one-shot from a rung one. */
    val lastFiredDate: LocalDate? = null
) {
    val spec: AlarmSpec
        get() = AlarmSpec(id, time, enabled, kind, schedule, oneShotDate, respectHolidays)

    companion object {
        const val UNLIMITED_SNOOZES = -1
        val WORKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}
