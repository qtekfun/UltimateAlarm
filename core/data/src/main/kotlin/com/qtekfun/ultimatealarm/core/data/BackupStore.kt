package com.qtekfun.ultimatealarm.core.data

import androidx.room3.withWriteTransaction
import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.data.db.DayOverrideEntity
import com.qtekfun.ultimatealarm.core.data.db.HolidayDecisionEntity
import com.qtekfun.ultimatealarm.core.data.db.HolidayOverrideEntity
import com.qtekfun.ultimatealarm.core.data.db.ManualHolidayEntity
import com.qtekfun.ultimatealarm.core.data.db.SkipEntity
import com.qtekfun.ultimatealarm.core.data.db.VacationAlarmEntity
import com.qtekfun.ultimatealarm.core.data.db.VacationEntity
import com.qtekfun.ultimatealarm.core.data.db.toAlarm
import com.qtekfun.ultimatealarm.core.data.db.toEntity
import com.qtekfun.ultimatealarm.core.data.db.toRule
import com.qtekfun.ultimatealarm.core.data.db.vacationRules
import com.qtekfun.ultimatealarm.core.scheduling.DayOverride
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.HolidayOverride
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.time.LocalDate

/** Everything a backup carries from the database (RF-B1). Automatic holidays are not included: they are downloaded. */
data class BackupContent(
    val alarms: List<Alarm> = emptyList(),
    val overrides: List<DayOverride> = emptyList(),
    val skips: List<SkipNext> = emptyList(),
    val vacations: List<Vacation> = emptyList(),
    val decisions: List<HolidayDecision> = emptyList(),
    val manualHolidays: List<ManualHoliday> = emptyList(),
    val holidayOverrides: List<HolidayOverride> = emptyList()
)

enum class RestoreMode {
    /** Delete what is there, then insert the backup with its ids. */
    REPLACE,

    /** Keep what is there and add the backup's alarms (new ids) and rules; identical vacations/holidays are not doubled. */
    MERGE
}

/** RF-B1/B5: database snapshot and restore, in one write transaction (§9). Tells [listener] afterwards to re-schedule. */
class BackupStore(private val db: AlarmDatabase, private val listener: AlarmsChangedListener = AlarmsChangedListener { }) {
    suspend fun snapshot(): BackupContent = BackupContent(
        alarms = db.alarmDao().all().map { it.toAlarm() },
        overrides = db.overrideDao().overrides().mapNotNull { it.toRule() },
        skips = db.skipDao().skips().map { it.toRule() },
        vacations = vacationRules(db.vacationDao().vacations(), db.vacationDao().vacationAlarms()),
        decisions = db.decisionDao().decisions().map { it.toRule() },
        manualHolidays = db.userHolidayDao().manual().map { ManualHoliday(it.id, LocalDate.ofEpochDay(it.date), it.name, it.yearly) },
        holidayOverrides = db.userHolidayDao().overrides().map { HolidayOverride(LocalDate.ofEpochDay(it.date), it.isHoliday, it.name) }
    )

    suspend fun restore(content: BackupContent, mode: RestoreMode) {
        db.withWriteTransaction {
            if (mode == RestoreMode.REPLACE) clearAll()
            val ids = insertAlarms(content.alarms, mode)
            insertRules(content, ids, mode)
        }
        listener.onAlarmsChanged("restore_${mode.name.lowercase()}")
    }

    private suspend fun clearAll() {
        db.alarmDao().deleteAll() // Cascades to exceptions, skips and vacation members.
        db.vacationDao().deleteAll()
        db.decisionDao().deleteAll()
        db.userHolidayDao().deleteAllManual()
        db.userHolidayDao().deleteAllOverrides()
    }

    /** Old id → id in this database. */
    private suspend fun insertAlarms(alarms: List<Alarm>, mode: RestoreMode): Map<Long, Long> = alarms.associate { alarm ->
        val row = if (mode == RestoreMode.MERGE) alarm.copy(id = 0) else alarm
        alarm.id to db.alarmDao().insert(row.toEntity())
    }

    private suspend fun insertRules(content: BackupContent, ids: Map<Long, Long>, mode: RestoreMode) {
        db.overrideDao().insertAll(content.overrides.mapNotNull { o -> ids[o.alarmId]?.let { DayOverrideEntity(it, o.date.toEpochDay(), o.mode.name) } })
        db.skipDao().insertAll(content.skips.mapNotNull { s -> ids[s.alarmId]?.let { SkipEntity(it, s.occurrenceDate.toEpochDay()) } })
        val existing = if (mode == RestoreMode.MERGE) vacationRules(db.vacationDao().vacations(), db.vacationDao().vacationAlarms()) else emptyList()
        content.vacations.forEach { vacation ->
            val members = vacation.alarmIds?.mapNotNull { ids[it] }?.toSet()
            if (existing.any { it.start == vacation.start && it.end == vacation.end && it.alarmIds == null && members == null }) return@forEach
            val id = db.vacationDao().upsertVacation(VacationEntity(0, vacation.start.toEpochDay(), vacation.end.toEpochDay(), members == null))
            members?.let { db.vacationDao().putVacationAlarms(it.map { alarm -> VacationAlarmEntity(id, alarm) }) }
        }
        db.decisionDao().insertAll(
            content.decisions.mapNotNull { d ->
                val alarm = d.alarmId?.let { ids[it] ?: return@mapNotNull null } ?: HolidayDecisionEntity.ALL_ALARMS
                HolidayDecisionEntity(d.date.toEpochDay(), alarm, d.skip)
            }
        )
        val manual = if (mode == RestoreMode.MERGE) db.userHolidayDao().manual() else emptyList()
        content.manualHolidays.filterNot { h -> manual.any { it.date == h.date.toEpochDay() && it.name == h.name && it.yearly == h.yearly } }.forEach {
            db.userHolidayDao().upsertManual(ManualHolidayEntity(0, it.date.toEpochDay(), it.name, it.yearly))
        }
        content.holidayOverrides.forEach { db.userHolidayDao().putOverride(HolidayOverrideEntity(it.date.toEpochDay(), it.isHoliday, it.name)) }
    }
}
