package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** RF-B5: replace and merge restore in one transaction, rules follow their alarms' new ids. */
@RunWith(AndroidJUnit4::class)
class BackupStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<AlarmDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val changes = mutableListOf<String>()
    private val alarms = AlarmRepository(db)
    private val holidays = HolidayRepository(db)
    private val store = BackupStore(db) { changes += it }
    private val day = LocalDate.of(2026, 10, 19)

    @After
    fun close() = db.close()

    private suspend fun seed(): Long {
        val id = alarms.save(Alarm(time = LocalTime.of(6, 30), label = "A"))
        alarms.setOverride(id, day, OverrideMode.FORCE_OFF)
        alarms.skip(id, day.plusDays(1))
        alarms.saveVacation(Vacation(0, day, day.plusDays(2), setOf(id)))
        alarms.decideHoliday(day, id, skip = true)
        holidays.saveManual(ManualHoliday(date = day, name = "Local", yearly = false))
        holidays.setCancelled(LocalDate.of(2026, 1, 6), cancelled = true)
        return id
    }

    @Test
    fun replaceRestoresExactlyTheSnapshot() = runTest {
        seed()
        val snapshot = store.snapshot()
        alarms.save(Alarm(label = "added later"))
        store.restore(snapshot, RestoreMode.REPLACE)
        val after = store.snapshot()
        assertEquals(snapshot.alarms, after.alarms)
        assertEquals(snapshot.overrides, after.overrides)
        assertEquals(snapshot.skips, after.skips)
        assertEquals(snapshot.vacations.map { it.copy(id = 0) }, after.vacations.map { it.copy(id = 0) })
        assertEquals(snapshot.decisions, after.decisions)
        assertEquals(snapshot.manualHolidays.map { it.copy(id = 0) }, after.manualHolidays.map { it.copy(id = 0) })
        assertEquals(snapshot.holidayOverrides, after.holidayOverrides)
        assertEquals("restore_replace", changes.last())
    }

    @Test
    fun mergeAddsAlarmsWithNewIdsAndRemapsTheirRules() = runTest {
        val id = seed()
        val snapshot = store.snapshot()
        store.restore(snapshot, RestoreMode.MERGE)
        val after = store.snapshot()
        assertEquals(2, after.alarms.size)
        val newId = after.alarms.map { it.id }.single { it != id }
        assertEquals(setOf(id, newId), after.overrides.map { it.alarmId }.toSet())
        assertEquals(setOf(id, newId), after.skips.map { it.alarmId }.toSet())
        assertEquals(listOf(setOf(id), setOf(newId)), after.vacations.map { it.alarmIds })
        assertEquals(setOf(HolidayDecision(day, id, true), HolidayDecision(day, newId, true)), after.decisions.toSet())
        // Identical manual holidays are not doubled.
        assertEquals(1, after.manualHolidays.size)
    }
}
