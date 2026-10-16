package com.qtekfun.ultimatealarm.feature.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockModelTest {
    private val minute = 60_000L

    @Test
    fun `timer runs, pauses, resumes and resets`() {
        val t = TimerState(1, durationMs = 5 * minute)
        assertFalse(t.running)
        val running = t.start(1_000)
        assertTrue(running.running)
        assertEquals(5 * minute, running.remaining(1_000))
        assertEquals(4 * minute, running.remaining(1_000 + minute))
        val paused = running.pause(1_000 + minute)
        assertFalse(paused.running)
        assertEquals(4 * minute, paused.remaining(99_999_999))
        val resumed = paused.start(10 * minute)
        assertEquals(10 * minute + 4 * minute, resumed.endAt)
        assertEquals(5 * minute, resumed.reset().remainingMs)
        assertEquals(0, running.remaining(1_000 + 10 * minute))
        assertEquals(running, running.start(5))
        assertEquals(paused, paused.pause(5))
    }

    @Test
    fun `plus one minute in every state`() {
        val t = TimerState(1, durationMs = minute)
        assertEquals(2 * minute, t.plus(minute, 0).remainingMs)
        val running = t.start(0)
        assertEquals(2 * minute, running.plus(minute, 10).endAt)
        val finished = running.finish()
        assertTrue(finished.finished)
        assertFalse(finished.running)
        val again = finished.plus(minute, 500)
        assertEquals(500 + minute, again.endAt)
        assertFalse(again.finished)
        assertEquals(finished, finished.start(0))
    }

    @Test
    fun `stopwatch accumulates across pauses and records laps`() {
        val s = StopwatchState().start(0)
        val lapped = s.lap(1_000).lap(3_500)
        assertEquals(listOf(1_000L, 3_500L), lapped.laps)
        assertEquals(listOf(1_000L, 2_500L), lapped.lapDurations())
        val paused = lapped.pause(4_000)
        assertEquals(4_000, paused.elapsed(100_000))
        assertEquals(paused, paused.lap(5_000))
        val resumed = paused.start(10_000)
        assertEquals(6_000, resumed.elapsed(12_000))
        assertEquals(resumed, resumed.start(11_000))
        assertEquals(StopwatchState(), resumed.reset())
        assertEquals(StopwatchState(), StopwatchState().pause(5))
    }

    @Test
    fun `clock parts`() {
        assertEquals(listOf(1L, 2L, 3L, 45L), clockParts(3_600_000 + 2 * 60_000 + 3_450))
        assertEquals(listOf(0L, 0L, 0L, 0L), clockParts(-5))
    }
}
