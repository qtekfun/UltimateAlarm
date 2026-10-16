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
