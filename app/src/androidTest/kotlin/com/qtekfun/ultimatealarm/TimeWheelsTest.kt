package com.qtekfun.ultimatealarm

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatealarm.core.designsystem.UltimateAlarmTheme
import com.qtekfun.ultimatealarm.feature.alarms.R
import com.qtekfun.ultimatealarm.feature.alarms.TimeWheels
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The time wheels on a real emulator: the minute that ends up in the highlighted centre row is the one reported, and
 * opening the wheels does not change the time (2026-10-17 report: alarms rang a minute after the time chosen).
 */
@RunWith(AndroidJUnit4::class)
class TimeWheelsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var time by mutableStateOf(LocalTime.of(6, 30))
    private val reported = mutableListOf<LocalTime>()

    private fun show() {
        compose.setContent {
            UltimateAlarmTheme {
                TimeWheels(time, {
                    reported += it
                    time = it
                })
            }
        }
        compose.waitForIdle()
    }

    private val minuteLabel get() = context.getString(R.string.wheel_minute)

    @Test
    fun openingTheWheelsKeepsTheTime() {
        show()
        compose.mainClock.advanceTimeBy(IDLE_MS)
        compose.waitForIdle()
        assertEquals(emptyList<LocalTime>(), reported)
        assertEquals(LocalTime.of(6, 30), time)
    }

    @Test
    fun theHighlightedMinuteIsTheOneReported() {
        show()
        // Slow drags of under one, one and a half and several rows, a quick fling, and drags back down.
        listOf(0.6f, 1.4f, 3.2f, -1.6f, -0.7f).forEach { rows -> dragMinutes(rows, durationMs = 600) }
        dragMinutes(4f, durationMs = 120)
        dragMinutes(-2.5f, durationMs = 120)
    }

    private fun dragMinutes(rows: Float, durationMs: Long) {
        val wheel = compose.onNodeWithContentDescription(minuteLabel, substring = true)
        val bounds = wheel.fetchSemanticsNode().boundsInRoot
        val row = bounds.height / VISIBLE_ROWS
        wheel.performTouchInput {
            val start = Offset(centerX, centerY + row * rows / 2)
            swipe(start, Offset(centerX, start.y - row * rows), durationMs)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(IDLE_MS)
        compose.waitForIdle()
        val centre = bounds.center.y
        val shown = compose.onAllNodes(hasAnyAncestor(hasContentDescription(minuteLabel, substring = true)) and hasText(TWO_DIGITS))
            .fetchSemanticsNodes()
            .single { it.boundsInRoot.top <= centre && it.boundsInRoot.bottom > centre }
            .config[SemanticsProperties.Text].single().text
        assertEquals("after dragging $rows rows the centre shows $shown", shown.toInt(), time.minute)
    }

    private fun hasText(regex: Regex) = SemanticsMatcher("text matches $regex") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { regex.matches(it.text) } == true
    }

    private companion object {
        const val IDLE_MS = 2_000L
        const val VISIBLE_ROWS = 3
        val TWO_DIGITS = Regex("\\d\\d")
    }
}
