package com.qtekfun.ultimatealarm

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the app starts and its main screen reaches the foreground (also on AOSP images without Google apps). */
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @Test
    fun mainActivityStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
