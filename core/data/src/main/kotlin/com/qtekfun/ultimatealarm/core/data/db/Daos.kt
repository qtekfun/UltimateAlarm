package com.qtekfun.ultimatealarm.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm ORDER BY timeOfDay, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm ORDER BY timeOfDay, id")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarm SET enabled = :enabled, missed = 0 WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarm SET lastFiredDate = :date, missed = 0 WHERE id = :id")
    suspend fun markFired(id: Long, date: Long)

    @Query("UPDATE alarm SET missed = 1 WHERE id = :id")
    suspend fun markMissed(id: Long)
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM day_override")
    fun observeOverrides(): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_override")
    suspend fun overrides(): List<DayOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOverride(override: DayOverrideEntity)

    @Query("DELETE FROM day_override WHERE alarmId = :alarmId AND date = :date")
    suspend fun deleteOverride(alarmId: Long, date: Long)
}

@Dao
interface SkipDao {
    @Query("SELECT * FROM skip_next")
    fun observeSkips(): Flow<List<SkipEntity>>

    @Query("SELECT * FROM skip_next")
    suspend fun skips(): List<SkipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSkip(skip: SkipEntity)

    @Query("DELETE FROM skip_next WHERE alarmId = :alarmId AND date = :date")
    suspend fun deleteSkip(alarmId: Long, date: Long)
}

@Dao
interface VacationDao {
    @Query("SELECT * FROM vacation ORDER BY start")
    fun observeVacations(): Flow<List<VacationEntity>>

    @Query("SELECT * FROM vacation ORDER BY start")
    suspend fun vacations(): List<VacationEntity>

    @Query("SELECT * FROM vacation_alarm")
    fun observeVacationAlarms(): Flow<List<VacationAlarmEntity>>

    @Query("SELECT * FROM vacation_alarm")
    suspend fun vacationAlarms(): List<VacationAlarmEntity>

    @Upsert
    suspend fun upsertVacation(vacation: VacationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVacationAlarms(rows: List<VacationAlarmEntity>)

    @Query("DELETE FROM vacation_alarm WHERE vacationId = :vacationId")
    suspend fun clearVacationAlarms(vacationId: Long)

    @Query("DELETE FROM vacation WHERE id = :id")
    suspend fun deleteVacation(id: Long)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM holiday_decision")
    fun observeDecisions(): Flow<List<HolidayDecisionEntity>>

    @Query("SELECT * FROM holiday_decision")
    suspend fun decisions(): List<HolidayDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDecision(decision: HolidayDecisionEntity)

    @Query("DELETE FROM holiday_decision WHERE date < :before")
    suspend fun deleteDecisionsBefore(before: Long)
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holiday ORDER BY date")
    fun observeHolidays(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holiday")
    suspend fun holidays(): List<HolidayEntity>

    @Query("SELECT COUNT(*) FROM holiday WHERE source = :source AND date BETWEEN :from AND :to")
    suspend fun count(source: String, from: Long, to: Long): Int

    @Query("DELETE FROM holiday WHERE source = :source AND date BETWEEN :from AND :to")
    suspend fun deleteRange(source: String, from: Long, to: Long)

    @Query("DELETE FROM holiday WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rows: List<HolidayEntity>)
}

/** Holidays the user added or cancelled. */
@Dao
interface UserHolidayDao {
    @Query("SELECT * FROM manual_holiday ORDER BY date")
    fun observeManual(): Flow<List<ManualHolidayEntity>>

    @Query("SELECT * FROM manual_holiday")
    suspend fun manual(): List<ManualHolidayEntity>

    @Upsert
    suspend fun upsertManual(row: ManualHolidayEntity): Long

    @Query("DELETE FROM manual_holiday WHERE id = :id")
    suspend fun deleteManual(id: Long)

    @Query("SELECT * FROM holiday_override")
    fun observeOverrides(): Flow<List<HolidayOverrideEntity>>

    @Query("SELECT * FROM holiday_override")
    suspend fun overrides(): List<HolidayOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOverride(row: HolidayOverrideEntity)

    @Query("DELETE FROM holiday_override WHERE date = :date")
    suspend fun deleteOverride(date: Long)
}
