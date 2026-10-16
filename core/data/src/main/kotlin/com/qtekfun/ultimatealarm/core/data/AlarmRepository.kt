package com.qtekfun.ultimatealarm.core.data

import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.data.db.DayOverrideEntity
import com.qtekfun.ultimatealarm.core.data.db.HolidayDecisionEntity
import com.qtekfun.ultimatealarm.core.data.db.SkipEntity
import com.qtekfun.ultimatealarm.core.data.db.VacationAlarmEntity
import com.qtekfun.ultimatealarm.core.data.db.VacationEntity
import com.qtekfun.ultimatealarm.core.data.db.toAlarm
import com.qtekfun.ultimatealarm.core.data.db.toEntity
import com.qtekfun.ultimatealarm.core.data.db.toRule
import com.qtekfun.ultimatealarm.core.data.db.vacationRules
import com.qtekfun.ultimatealarm.core.scheduling.HolidayCalendar
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.RuleSet
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Told after every write so the alarms registered with the system follow the database (02-diseno §4). */
fun interface AlarmsChangedListener {
    suspend fun onAlarmsChanged(reason: String)
}

/** Holidays come from `:feature:holidays` (Phase 6); until then there are none. */
fun interface HolidaySource {
    suspend fun calendar(): HolidayCalendar
}

/**
 * Alarms and the rules around them. Every write tells [listener], which re-schedules; reads are flows for the UI and
 * suspend snapshots for the re-scheduler.
 */
class AlarmRepository(
    db: AlarmDatabase,
    private val holidays: HolidaySource = HolidaySource { HolidayCalendar.NONE },
    private val listener: AlarmsChangedListener = AlarmsChangedListener { }
) {
    private val alarms = db.alarmDao()
    private val overrides = db.overrideDao()
    private val skips = db.skipDao()
    private val vacations = db.vacationDao()
    private val decisions = db.decisionDao()

    fun observeAlarms(): Flow<List<Alarm>> = alarms.observeAll().map { rows -> rows.map { it.toAlarm() } }

    /** Rules without holidays; the caller adds the calendar it observes. */
    fun observeRules(): Flow<RuleSet> = combine(
        overrides.observeOverrides(),
        skips.observeSkips(),
        vacations.observeVacations(),
        vacations.observeVacationAlarms(),
        decisions.observeDecisions()
    ) { overrides, skips, vacations, members, decisions ->
        RuleSet(
            overrides = overrides.mapNotNull { it.toRule() },
            skips = skips.map { it.toRule() },
            vacations = vacationRules(vacations, members),
            decisions = decisions.map { it.toRule() }
        )
    }

    fun observeVacations(): Flow<List<Vacation>> = combine(vacations.observeVacations(), vacations.observeVacationAlarms(), ::vacationRules)

    suspend fun holidayCalendar(): HolidayCalendar = holidays.calendar()

    /** Time until the next occurrence of [id], or `null` if it will not ring (RF-A3). */
    suspend fun ringsIn(id: Long, now: ZonedDateTime = ZonedDateTime.now()): Duration? {
        val alarm = alarm(id) ?: return null
        return SchedulingEngine.nextOccurrence(alarm.spec, rules(), now)?.let { Duration.between(now, it.at) }
    }

    suspend fun alarms(): List<Alarm> = alarms.all().map { it.toAlarm() }

    suspend fun alarm(id: Long): Alarm? = alarms.byId(id)?.toAlarm()

    suspend fun rules(): RuleSet = RuleSet(
        overrides = overrides.overrides().mapNotNull { it.toRule() },
        skips = skips.skips().map { it.toRule() },
        vacations = vacationRules(vacations.vacations(), vacations.vacationAlarms()),
        holidays = holidays.calendar(),
        decisions = decisions.decisions().map { it.toRule() }
    )

    /** Inserts when `id == 0`, updates otherwise. Returns the id. Editing clears the missed flag. */
    suspend fun save(alarm: Alarm): Long {
        val id = if (alarm.id ==
            0L
        ) {
            alarms.insert(alarm.copy(missed = false).toEntity())
        } else {
            alarm.id.also { alarms.upsert(alarm.copy(missed = false).toEntity()) }
        }
        listener.onAlarmsChanged("save")
        return id
    }

    /** Re-inserts a deleted alarm with its old id (undo). */
    suspend fun restore(alarm: Alarm) {
        alarms.upsert(alarm.toEntity())
        listener.onAlarmsChanged("restore")
    }

    suspend fun duplicate(id: Long): Long? {
        val original = alarm(id) ?: return null
        return save(original.copy(id = 0, missed = false, lastFiredDate = null))
    }

    suspend fun delete(id: Long) {
        alarms.delete(id)
        listener.onAlarmsChanged("delete")
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        alarms.setEnabled(id, enabled)
        listener.onAlarmsChanged("toggle")
    }

    /** Records a firing without re-scheduling: the ringing path does that itself. */
    suspend fun markFired(id: Long, date: LocalDate) = alarms.markFired(id, date.toEpochDay())

    suspend fun markMissed(id: Long) = alarms.markMissed(id)

    suspend fun skip(alarmId: Long, date: LocalDate) {
        skips.putSkip(SkipEntity(alarmId, date.toEpochDay()))
        listener.onAlarmsChanged("skip")
    }

    suspend fun unskip(alarmId: Long, date: LocalDate) {
        skips.deleteSkip(alarmId, date.toEpochDay())
        listener.onAlarmsChanged("unskip")
    }

    /** Removes skips whose occurrence has passed (02-diseno §2: consumed when it passes). No re-scheduling needed. */
    suspend fun dropSkips(stale: Collection<Pair<Long, LocalDate>>) = stale.forEach { (id, date) -> skips.deleteSkip(id, date.toEpochDay()) }

    /** `null` mode removes the exception ("según patrón"). */
    suspend fun setOverride(alarmId: Long, date: LocalDate, mode: OverrideMode?) {
        if (mode ==
            null
        ) {
            overrides.deleteOverride(alarmId, date.toEpochDay())
        } else {
            overrides.putOverride(DayOverrideEntity(alarmId, date.toEpochDay(), mode.name))
        }
        listener.onAlarmsChanged("override")
    }

    suspend fun saveVacation(vacation: Vacation): Long {
        val id = vacations.upsertVacation(VacationEntity(vacation.id, vacation.start.toEpochDay(), vacation.end.toEpochDay(), vacation.alarmIds == null))
            .let { if (vacation.id == 0L) it else vacation.id }
        vacations.clearVacationAlarms(id)
        vacation.alarmIds?.let { ids -> vacations.putVacationAlarms(ids.map { VacationAlarmEntity(id, it) }) }
        listener.onAlarmsChanged("vacation")
        return id
    }

    suspend fun deleteVacation(id: Long) {
        vacations.deleteVacation(id)
        listener.onAlarmsChanged("vacation")
    }

    /** RF-F7 answer; `alarmId == null` answers for every alarm. */
    suspend fun decideHoliday(date: LocalDate, alarmId: Long?, skip: Boolean) {
        decisions.putDecision(HolidayDecisionEntity(date.toEpochDay(), alarmId ?: HolidayDecisionEntity.ALL_ALARMS, skip))
        listener.onAlarmsChanged("holiday_decision")
    }

    suspend fun dropDecisionsBefore(date: LocalDate) = decisions.deleteDecisionsBefore(date.toEpochDay())
}
