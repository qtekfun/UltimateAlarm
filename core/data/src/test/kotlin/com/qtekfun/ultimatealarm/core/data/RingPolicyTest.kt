package com.qtekfun.ultimatealarm.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPolicyTest {
    private val alarm = Alarm(maxVolume = 80, rampSeconds = 30, maxSnoozes = 2, backupAfterMinutes = 5)

    @Test
    fun `ramp starts barely audible and reaches the maximum volume`() {
        assertEquals(0.8f * 0.02f, RingPolicy.gain(0, 30, 80), 1e-4f)
        assertEquals(0.8f, RingPolicy.gain(30_000, 30, 80), 1e-4f)
        assertEquals(0.8f, RingPolicy.gain(90_000, 30, 80), 1e-4f)
        val half = RingPolicy.gain(15_000, 30, 80)
        assertTrue(half > 0.8f * 0.02f && half < 0.8f * 0.5f)
    }

    @Test
    fun `ramp is monotonic`() {
        val gains = (0..30).map { RingPolicy.gain(it * 1_000L, 30, 100) }
        assertEquals(gains.sorted(), gains)
    }

    @Test
    fun `no ramp, clamped volume and backup gain`() {
        assertEquals(0.5f, RingPolicy.gain(0, 0, 50), 1e-4f)
        assertEquals(1f, RingPolicy.gain(0, 0, 250), 1e-4f)
        assertEquals(0.01f, RingPolicy.gain(10_000, 5, 0), 1e-4f)
        assertEquals(1f, RingPolicy.gain(0, 300, 10, RingMode.BACKUP), 1e-4f)
        assertEquals(0.01f * 0.01f, RingPolicy.gain(-5, 30, 1), 1e-5f)
    }

    @Test
    fun `snooze limits`() {
        assertTrue(RingPolicy.canSnooze(1, 2))
        assertFalse(RingPolicy.canSnooze(2, 2))
        assertFalse(RingPolicy.canSnooze(0, 0))
        assertTrue(RingPolicy.canSnooze(99, Alarm.UNLIMITED_SNOOZES))
    }

    @Test
    fun `ring after the last allowed snooze is a backup ring`() {
        assertEquals(RingMode.NORMAL, RingPolicy.modeForRing(0, alarm))
        assertEquals(RingMode.NORMAL, RingPolicy.modeForRing(1, alarm))
        assertEquals(RingMode.BACKUP, RingPolicy.modeForRing(2, alarm))
        assertEquals(RingMode.NORMAL, RingPolicy.modeForRing(2, alarm.copy(backupAfterMinutes = null)))
        assertEquals(RingMode.NORMAL, RingPolicy.modeForRing(5, alarm.copy(maxSnoozes = Alarm.UNLIMITED_SNOOZES)))
        assertEquals(RingMode.NORMAL, RingPolicy.modeForRing(0, alarm.copy(maxSnoozes = 0)))
    }

    @Test
    fun `backup time`() {
        assertEquals(1_000L + 300_000L, RingPolicy.backupAt(1_000, alarm))
        assertNull(RingPolicy.backupAt(1_000, alarm.copy(backupAfterMinutes = null)))
    }

    @Test
    fun `silence time`() {
        val noBackup = alarm.copy(backupAfterMinutes = null)
        assertEquals(30 * 60_000L, RingPolicy.silenceAt(0, noBackup, null))
        assertEquals(60 * 60_000L, RingPolicy.silenceAt(0, alarm, null))
        assertEquals(10 * 60_000L, RingPolicy.silenceAt(0, alarm, 10))
        assertEquals(60 * 60_000L, RingPolicy.silenceAt(0, alarm, 600))
    }

    @Test
    fun `resumable rings`() {
        val state = RingState(1, firedAt = 1_000_000, snoozeCount = 0, backupFired = false)
        assertTrue(RingPolicy.isResumable(state, 1_000_000 + 59 * 60_000))
        assertFalse(RingPolicy.isResumable(state, 1_000_000 + 61 * 60_000))
        assertFalse(RingPolicy.isResumable(state, 999_000))
    }
}
