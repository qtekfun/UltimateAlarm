package com.qtekfun.ultimatealarm.feature.clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** RF-E2/E3 on a real emulator: a short timer fires through AlarmManager and rings; the stopwatch survives a reload. */
@RunWith(AndroidJUnit4::class)
class TimerFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ClockStore.get(context)

    @Before
    @After
    fun clean() {
        store.timers.value.forEach { Timers.delete(context, it.id) }
        TimerService.Ringing.current.value.forEach { TimerService.send(context, TimerService.ACTION_STOP, it) }
        Stopwatch.reset(context)
    }

    @Test
    fun shortTimerRingsAndStopResetsIt() {
        Timers.add(context, 3_000, "test")
        val id = store.timers.value.single().id
        assertTrue(store.timers.value.single().running)
        assertNotNull("timer did not ring", waitFor(20_000) { TimerService.Ringing.current.value.takeIf { id in it } })
        assertTrue(store.timers.value.single().finished)

        TimerService.send(context, TimerService.ACTION_STOP, id)
        assertNotNull(waitFor(5_000) { TimerService.Ringing.current.value.takeIf { it.isEmpty() } })
        val timer = store.timers.value.single()
        assertFalse(timer.finished)
        assertEquals(3_000, timer.remainingMs)
    }

    @Test
    fun stopwatchStateIsPersisted() {
        Stopwatch.toggle(context)
        Thread.sleep(300)
        Stopwatch.lap(context)
        Stopwatch.toggle(context)
        val state = store.stopwatch.value
        assertFalse(state.running)
        assertEquals(1, state.laps.size)
        assertTrue(state.accumulatedMs >= 300)
    }

    private fun <T> waitFor(timeoutMs: Long, probe: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            probe()?.let { return it }
            Thread.sleep(200)
        }
        return probe()
    }
}
