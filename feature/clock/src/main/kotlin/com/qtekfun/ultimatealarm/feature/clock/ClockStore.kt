package com.qtekfun.ultimatealarm.feature.clock

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatealarm.core.data.DeviceStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * Timers and the stopwatch, persisted in device-protected preferences so they survive the app being closed, the process
 * being killed and reboots (RF-E2, RF-E3). Observable for the UI; every change goes through [update*].
 */
class ClockStore private constructor(context: Context) {
    private val prefs = DeviceStorage.context(context).getSharedPreferences("clock", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val timersState = MutableStateFlow(readTimers())
    val timers: StateFlow<List<TimerState>> = timersState

    private val stopwatchState = MutableStateFlow(readStopwatch())
    val stopwatch: StateFlow<StopwatchState> = stopwatchState

    @Synchronized
    fun updateTimers(change: (List<TimerState>) -> List<TimerState>): List<TimerState> {
        val next = change(timersState.value)
        prefs.edit(commit = true) { putString(KEY_TIMERS, json.encodeToString(next)) }
        timersState.value = next
        return next
    }

    @Synchronized
    fun updateStopwatch(change: (StopwatchState) -> StopwatchState): StopwatchState {
        val next = change(stopwatchState.value)
        prefs.edit(commit = true) { putString(KEY_STOPWATCH, json.encodeToString(next)) }
        stopwatchState.value = next
        return next
    }

    @Synchronized
    fun nextTimerId(): Long = prefs.getLong(KEY_NEXT_ID, 1).also { prefs.edit(commit = true) { putLong(KEY_NEXT_ID, it + 1) } }

    private fun readTimers(): List<TimerState> = runCatching {
        json.decodeFromString<List<TimerState>>(prefs.getString(KEY_TIMERS, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun readStopwatch(): StopwatchState = runCatching {
        json.decodeFromString<StopwatchState>(prefs.getString(KEY_STOPWATCH, null) ?: "{}")
    }.getOrDefault(StopwatchState())

    companion object {
        private const val KEY_TIMERS = "timers"
        private const val KEY_STOPWATCH = "stopwatch"
        private const val KEY_NEXT_ID = "next_timer_id"

        @Volatile
        private var instance: ClockStore? = null

        fun get(context: Context): ClockStore = instance ?: synchronized(this) {
            instance ?: ClockStore(context.applicationContext).also { instance = it }
        }
    }
}
