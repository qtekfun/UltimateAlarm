package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.core.data.db.AlarmDatabase
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<AlarmDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val changes = mutableListOf<String>()
    private val repository = AlarmRepository(db, listener = { changes += it })
    private val day = LocalDate.of(2026, 10, 19)

    @After
    fun close() = db.close()

    @Test
    fun saveUpdateDuplicateDelete() = runTest {
        val id = repository.save(Alarm(time = LocalTime.of(6, 30), label = "A"))
        repository.save(repository.alarm(id)!!.copy(label = "B"))
        assertEquals("B", repository.alarm(id)!!.label)
        val copy = repository.duplicate(id)!!
        assertEquals(2, repository.observeAlarms().first().size)
        assertEquals("B", repository.alarm(copy)!!.label)
        repository.delete(id)
        assertNull(repository.alarm(id))
        assertEquals(listOf("save", "save", "save", "delete"), changes)
    }

    @Test
    fun restoreKeepsTheId() = runTest {
        val id = repository.save(Alarm(label = "A"))
        val alarm = repository.alarm(id)!!
        repository.delete(id)
        repository.restore(alarm)
        assertEquals(alarm, repository.alarm(id))
    }

    @Test
    fun rulesAreStoredAndCascade() = runTest {
        val id = repository.save(Alarm())
        repository.skip(id, day)
        repository.setOverride(id, day.plusDays(1), OverrideMode.FORCE_ON)
        repository.saveVacation(Vacation(0, day, day.plusDays(3), setOf(id)))
        repository.decideHoliday(day, null, skip = true)
        val rules = repository.rules()
        assertEquals(listOf(SkipNext(id, day)), rules.skips)
        assertEquals(OverrideMode.FORCE_ON, rules.overrides.single().mode)
        assertEquals(setOf(id), rules.vacations.single().alarmIds)
        assertEquals(listOf(HolidayDecision(day, null, true)), rules.decisions)
        assertEquals(rules.copy(holidays = repository.observeRules().first().holidays), repository.observeRules().first())

        repository.delete(id)
        val after = repository.rules()
        assertTrue(after.skips.isEmpty() && after.overrides.isEmpty())
        assertEquals(emptySet<Long>(), after.vacations.single().alarmIds)
    }

    @Test
    fun unskipOverrideRemovalAndVacationEdit() = runTest {
        val id = repository.save(Alarm())
        repository.skip(id, day)
        repository.unskip(id, day)
        repository.setOverride(id, day, OverrideMode.FORCE_OFF)
        repository.setOverride(id, day, null)
        val vacation = repository.saveVacation(Vacation(0, day, day, null))
        repository.saveVacation(Vacation(vacation, day, day.plusDays(1), null))
        assertEquals(day.plusDays(1), repository.observeVacations().first().single().end)
        repository.deleteVacation(vacation)
        val rules = repository.rules()
        assertTrue(rules.skips.isEmpty() && rules.overrides.isEmpty() && rules.vacations.isEmpty())
    }

    @Test
    fun firedMissedAndToggle() = runTest {
        val id = repository.save(Alarm())
        repository.markMissed(id)
        assertTrue(repository.alarm(id)!!.missed)
        repository.markFired(id, day)
        assertEquals(day, repository.alarm(id)!!.lastFiredDate)
        assertEquals(false, repository.alarm(id)!!.missed)
        repository.setEnabled(id, false)
        assertEquals(false, repository.alarm(id)!!.enabled)
    }
}
