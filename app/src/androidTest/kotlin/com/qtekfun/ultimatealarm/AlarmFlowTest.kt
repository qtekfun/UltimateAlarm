package com.qtekfun.ultimatealarm

import android.app.AlarmManager
import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.feature.alarms.TAG_ADD_ALARM
import com.qtekfun.ultimatealarm.feature.alarms.TAG_SAVE_ALARM
import com.qtekfun.ultimatealarm.feature.ringing.AlarmGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 3 end to end: create an alarm from the list, save it, see its status and find it registered in AlarmManager. */
@RunWith(AndroidJUnit4::class)
class AlarmFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clean() = runBlocking {
        val repository = AlarmGraph.repository(context)
        repository.alarms().forEach { repository.delete(it.id) }
    }

    @Test
    fun createAlarmShowsStatusAndRegistersIt() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasTestTag(TAG_ADD_ALARM)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(TAG_ADD_ALARM).performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasTestTag(TAG_SAVE_ALARM)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(TAG_SAVE_ALARM).performClick()
        compose.waitUntil(TIMEOUT_MS) {
            runBlocking { AlarmGraph.repository(context).alarms().isNotEmpty() } &&
                context.getSystemService(AlarmManager::class.java).nextAlarmClock != null
        }
        // The default alarm repeats Mon–Fri, so it rings within a week.
        assertNotNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText("Rings in", substring = true).or(hasText("Suena en", substring = true))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
