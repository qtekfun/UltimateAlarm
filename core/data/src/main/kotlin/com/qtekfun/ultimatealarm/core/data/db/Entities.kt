package com.qtekfun.ultimatealarm.core.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Dates are epoch days, times are seconds of the day, enums are stored by name. */
@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val timeOfDay: Int,
    val label: String,
    val enabled: Boolean,
    val kind: String,
    /** WEEKLY or CYCLE. */
    val scheduleType: String,
    /** Bit `n` set = DayOfWeek with value `n + 1`. */
    val weekDays: Int,
    val cycleAnchor: Long?,
    /** '1' rings, '0' does not, one character per day of the cycle. */
    val cycleMask: String?,
    val oneShotDate: Long?,
    val respectHolidays: Boolean,
    val sound: String?,
    val backupSound: String?,
    val maxVolume: Int,
    val rampSeconds: Int,
    val vibrate: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
    val backupAfterMinutes: Int?,
    val challenge: String?,
    val preNotifyMinutes: Int?,
    val missed: Boolean,
    val lastFiredDate: Long?
)

@Entity(
    tableName = "day_override",
    primaryKeys = ["alarmId", "date"],
    foreignKeys = [ForeignKey(entity = AlarmEntity::class, parentColumns = ["id"], childColumns = ["alarmId"], onDelete = ForeignKey.CASCADE)]
)
data class DayOverrideEntity(val alarmId: Long, val date: Long, val mode: String)

@Entity(
    tableName = "skip_next",
    primaryKeys = ["alarmId", "date"],
    foreignKeys = [ForeignKey(entity = AlarmEntity::class, parentColumns = ["id"], childColumns = ["alarmId"], onDelete = ForeignKey.CASCADE)]
)
data class SkipEntity(val alarmId: Long, val date: Long)

@Entity(tableName = "vacation")
data class VacationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val start: Long,
    val end: Long,
    /** true: every alarm; false: the alarms in [VacationAlarmEntity]. */
    val allAlarms: Boolean
)

@Entity(
    tableName = "vacation_alarm",
    primaryKeys = ["vacationId", "alarmId"],
    indices = [Index("alarmId")],
    foreignKeys = [
        ForeignKey(entity = VacationEntity::class, parentColumns = ["id"], childColumns = ["vacationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AlarmEntity::class, parentColumns = ["id"], childColumns = ["alarmId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class VacationAlarmEntity(val vacationId: Long, val alarmId: Long)

/** [alarmId] = [ALL_ALARMS] answers for every alarm on [date]. */
@Entity(tableName = "holiday_decision", primaryKeys = ["date", "alarmId"])
data class HolidayDecisionEntity(val date: Long, val alarmId: Long, val skip: Boolean) {
    companion object {
        const val ALL_ALARMS = 0L
    }
}
