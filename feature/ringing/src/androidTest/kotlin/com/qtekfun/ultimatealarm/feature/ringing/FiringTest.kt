package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end firing with the screen off: a real `setAlarmClock` alarm a few seconds ahead must start the ringing
 * service, play a sound and log the whole path (03-ci-release.md, instrumented.yml).
 */
@RunWith(AndroidJUnit4::class)
class FiringTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun stop() {
        context.startService(RingingService.command(context, RingingService.ACTION_DISMISS, AlarmScheduler.AUTOTEST_ID))
        shell("input keyevent KEYCODE_WAKEUP")
    }

    @Test
    fun alarmFiresWithTheScreenOff() {
        DiagnosticLog.get(context).clear()
        shell("input keyevent KEYCODE_SLEEP")
        AlarmScheduler.schedule(context, AlarmScheduler.AUTOTEST_ID, System.currentTimeMillis() + 5_000)

        val ringing = waitFor(30_000) { RingingState.current.value }
        assertNotNull("the alarm did not ring", ringing)

        val types = waitFor(10_000) {
            DiagnosticLog.get(context).read().map { it.type }.takeIf { "SOUND" in it && "FGS_STARTED" in it }
        }
        assertNotNull("incomplete log: ${DiagnosticLog.get(context).read()}", types)
        assertTrue(types!!.containsAll(listOf("SCHEDULED", "FIRED", "FGS_STARTED", "SOUND")))
        val sound = DiagnosticLog.get(context).read().last { it.type == "SOUND" }
        assertTrue("nothing played: ${sound.detail}", !sound.detail.startsWith("NOTHING"))
    }

    private fun <T> waitFor(timeoutMs: Long, probe: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            probe()?.let { return it }
            Thread.sleep(250)
        }
        return probe()
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }
}
