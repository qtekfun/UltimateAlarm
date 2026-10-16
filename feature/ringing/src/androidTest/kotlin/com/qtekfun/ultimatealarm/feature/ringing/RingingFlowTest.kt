package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.RingMode
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 4 on a real emulator: snooze bookkeeping, backup take-over, one-shot deletion on dismiss. */
@RunWith(AndroidJUnit4::class)
class RingingFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = AlarmGraph.repository(context)

    @Before
    @After
    fun clean() = runBlocking {
        RingingState.current.value?.let { context.startService(RingingService.command(context, RingingService.ACTION_DISMISS, it.alarmId)) }
        waitFor(5_000) { RingingState.current.value == null }
        repository.alarms().forEach { repository.delete(it.id) }
        RingStore(context).state = null
    }

    @Test
    fun snoozeRegistersTheSnoozeAndDismissWhileSnoozedClearsIt() = runBlocking {
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), snoozeMinutes = 7, maxSnoozes = 1, backupAfterMinutes = null))
        ring(id)
        context.startService(RingingService.command(context, RingingService.ACTION_SNOOZE, id))
        assertNotNull(waitFor(5_000) { RingStore(context).snoozedUntil[id] })
        val until = RingStore(context).snoozedUntil.getValue(id)
        assertTrue(until - System.currentTimeMillis() in 6 * 60_000L..7 * 60_000L)
        assertEquals(1, RingStore(context).snoozesOf(id))
        assertNotNull("still ringing after snooze", waitFor(5_000) { if (RingingState.current.value == null) Unit else null })
        // The snooze is the next alarm clock the system knows about.
        assertEquals(until, context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime)

        RingingService.endSnoozed(context, id)
        assertNull(RingStore(context).snoozedUntil[id])
        assertEquals(0, RingStore(context).snoozesOf(id))
    }

    @Test
    fun ringAfterTheLastSnoozeIsTheBackupRing() = runBlocking {
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), maxSnoozes = 1, backupAfterMinutes = 5))
        RingStore(context).setSnoozes(id, 1)
        context.startForegroundService(RingingService.startIntent(context, id, System.currentTimeMillis(), AlarmPurpose.SNOOZE))
        assertNotNull("did not switch to backup", waitFor(10_000) { RingingState.current.value?.takeIf { it.mode == RingMode.BACKUP } })
        assertEquals(false, RingingState.current.value?.canSnooze)
        assertTrue(DiagnosticLog.get(context).read().any { it.type == "BACKUP" && it.alarmId == id })
    }

    @Test
    fun safetyBackupAlarmSwitchesARingingAlarmToBackup() = runBlocking {
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = 5))
        ring(id)
        context.startForegroundService(RingingService.startIntent(context, id, System.currentTimeMillis(), AlarmPurpose.BACKUP))
        assertNotNull(waitFor(5_000) { RingingState.current.value?.takeIf { it.mode == RingMode.BACKUP } })
        assertTrue(RingStore(context).state!!.backupFired)
    }

    @Test
    fun dismissingAOneShotDeletesIt() = runBlocking {
        val id = repository.save(Alarm(kind = AlarmKind.ONE_SHOT, oneShotDate = LocalDate.now().plusDays(1), respectHolidays = false))
        ring(id)
        context.startService(RingingService.command(context, RingingService.ACTION_DISMISS, id))
        assertNotNull("one-shot not deleted", waitFor(5_000) { runBlocking { repository.alarm(id) }.let { if (it == null) Unit else null } })
        assertNull(RingStore(context).state)
    }

    @Test
    fun commandForAnotherAlarmDoesNotSilenceTheRingingOne() = runBlocking {
        val ringing = repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = null))
        val other = repository.save(Alarm(time = LocalTime.of(4, 0), backupAfterMinutes = null))
        ring(ringing)
        context.startService(RingingService.command(context, RingingService.ACTION_DISMISS, other))
        context.startService(RingingService.command(context, RingingService.ACTION_SNOOZE, other))
        Thread.sleep(1_000)
        assertEquals(ringing, RingingState.current.value?.alarmId)
    }

    @Test
    fun missingOwnFileFallsBackToThePackTone() = runBlocking {
        DiagnosticLog.get(context).clear()
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), sound = "file:content://com.example.gone/tone.mp3", backupAfterMinutes = null))
        ring(id)
        val sound =
            waitFor(15_000) { DiagnosticLog.get(context).read().lastOrNull { it.type == "SOUND" && it.alarmId == id && it.detail.contains("fallback") } }
        assertNotNull("no fallback logged: ${DiagnosticLog.get(context).read().filter { it.type == "SOUND" }}", sound)
        assertTrue(sound!!.detail, sound.detail.startsWith("fallback bundled:dawn_marimba"))
    }

    private fun ring(id: Long) {
        context.startForegroundService(RingingService.startIntent(context, id, System.currentTimeMillis()))
        assertNotNull("did not ring", waitFor(10_000) { RingingState.current.value?.takeIf { it.alarmId == id && it.title.isNotEmpty() } })
    }

    private fun <T> waitFor(timeoutMs: Long, probe: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            probe()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return probe()
    }

    private companion object {
        const val POLL_MS = 200L
    }
}
