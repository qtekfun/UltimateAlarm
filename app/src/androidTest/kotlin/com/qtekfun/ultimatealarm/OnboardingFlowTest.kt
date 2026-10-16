package com.qtekfun.ultimatealarm

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.feature.alarms.TAG_ADD_ALARM
import com.qtekfun.ultimatealarm.feature.onboarding.OnboardingStore
import com.qtekfun.ultimatealarm.feature.onboarding.R as OnboardingR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** RF-O1: the assistant greets a first run, can be skipped, and is not shown again. */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun firstRunShowsTheAssistantThenTheAlarms() {
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        ActivityScenario.launch(MainActivity::class.java).use {
            val welcome = context.getString(OnboardingR.string.onboarding_welcome_title)
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText(welcome)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(context.getString(OnboardingR.string.onboarding_skip)).performClick()
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasTestTag(TAG_ADD_ALARM)).fetchSemanticsNodes().isNotEmpty() }
        }
        assertTrue(OnboardingStore.isDone(context))
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
