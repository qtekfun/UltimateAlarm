package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.content.Context
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.Challenge
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.RingMode
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.data.VolumeButtonAction
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.feature.ringing.challenge.hasNfc
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
        RingStore(context).turnScreenOn = true
        RingStore(context).volumeButtons = VolumeButtonAction.SNOOZE
        shell("input keyevent KEYCODE_WAKEUP")
    }

    @Test
    fun volumeKeySnoozesWithTheScreenOff() = runBlocking {
        RingStore(context).turnScreenOn = false
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = null))
        ring(id)
        screenOff()
        // With the screen off the ringing screen gets no keys: the service's media session must take them.
        pressVolumeDown()
        assertNotNull("volume key did not snooze", waitFor(5_000) { RingStore(context).snoozedUntil[id] })
        assertNull(RingingState.current.value)
    }

    @Test
    fun volumeKeySetToNothingKeepsRinging() = runBlocking {
        RingStore(context).turnScreenOn = false
        RingStore(context).volumeButtons = VolumeButtonAction.NOTHING
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = null))
        ring(id)
        screenOff()
        pressVolumeDown()
        Thread.sleep(1_000)
        assertEquals(id, RingingState.current.value?.alarmId)
        assertNull(RingStore(context).snoozedUntil[id])
    }

    @Test
    fun ringTurnsTheScreenOnOnlyWhenChosen() = runBlocking {
        val power = context.getSystemService(PowerManager::class.java)
        RingStore(context).turnScreenOn = false
        screenOff()
        ring(repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = null)))
        Thread.sleep(2_000)
        assertTrue("screen turned on although disabled", !power.isInteractive)
        clean()

        RingStore(context).turnScreenOn = true
        screenOff()
        ring(repository.save(Alarm(time = LocalTime.of(4, 0), backupAfterMinutes = null)))
        assertNotNull("screen did not turn on", waitFor(5_000) { if (power.isInteractive) Unit else null })
    }

    private fun screenOff() {
        shell("input keyevent KEYCODE_SLEEP")
        val power = context.getSystemService(PowerManager::class.java)
        assertNotNull("screen did not go off", waitFor(5_000) { if (power.isInteractive) null else Unit })
    }

    /**
     * A hardware volume key with the screen off is not dispatched to any window: the system hands it straight to the
     * media sessions, as [AudioManager.adjustVolume] does. `input keyevent` would not do: an injected key is dispatched
     * even with the screen off, and on API 31 the stopped ringing screen, focused but without a focused window, then
     * gets an ANR that kills the test process.
     */
    private fun pressVolumeDown() {
        context.getSystemService(AudioManager::class.java).adjustVolume(AudioManager.ADJUST_LOWER, 0)
    }

    private fun shell(command: String) {
        // Reading the output waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        Thread.sleep(SHELL_SETTLE_MS)
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
        // The event log is written on a background thread: wait for the entry.
        assertNotNull(waitFor(5_000) { DiagnosticLog.get(context).read().firstOrNull { it.type == "BACKUP" && it.alarmId == id } })
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

    @Test
    fun challengeIsExposedAndDegradesWithoutHardware() = runBlocking {
        val id = repository.save(Alarm(time = LocalTime.of(3, 0), challenge = Challenge.Nfc("04AABB").encode(), backupAfterMinutes = null))
        ring(id)
        val expected = if (hasNfc(context)) Challenge.Nfc("04AABB") else Challenge.Math()
        assertEquals(expected, RingingState.current.value?.challenge)
    }

    @Test
    fun alarmFiringRightAfterADismissKeepsRinging() = runBlocking {
        val first = repository.save(Alarm(time = LocalTime.of(3, 0), backupAfterMinutes = null))
        val second = repository.save(Alarm(time = LocalTime.of(4, 0), backupAfterMinutes = null))
        ring(first)
        // Dismiss and a new ring back to back: the stop of the first must not take the second down.
        context.startService(RingingService.command(context, RingingService.ACTION_DISMISS, first))
        context.startForegroundService(RingingService.startIntent(context, second, System.currentTimeMillis()))
        assertNotNull("second alarm did not ring", waitFor(10_000) { RingingState.current.value?.takeIf { it.alarmId == second && it.title.isNotEmpty() } })
        Thread.sleep(2_000)
        assertEquals(second, RingingState.current.value?.alarmId)
    }

    private fun ring(id: Long) {
        context.startForegroundService(RingingService.startIntent(context, id, System.currentTimeMillis()))
        assertNotNull("did not ring", waitFor(20_000) { RingingState.current.value?.takeIf { it.alarmId == id && it.title.isNotEmpty() } })
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
        const val SHELL_SETTLE_MS = 500L
    }
}
