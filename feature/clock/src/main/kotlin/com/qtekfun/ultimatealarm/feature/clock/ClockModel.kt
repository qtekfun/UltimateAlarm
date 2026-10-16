package com.qtekfun.ultimatealarm.feature.clock

import kotlinx.serialization.Serializable

/**
 * RF-E2: one timer. Running when [endAt] is set (epoch millis); paused or not started otherwise, with [remainingMs] left.
 * Pure state transitions, so the rules are unit-tested; the store persists the list.
 */
@Serializable
data class TimerState(
    val id: Long,
    val label: String = "",
    val durationMs: Long,
    val remainingMs: Long = durationMs,
    val endAt: Long? = null,
    /** Set when the timer reached zero and is ringing / waiting to be stopped. */
    val finished: Boolean = false
) {
    val running: Boolean get() = endAt != null && !finished

    fun remaining(now: Long): Long = endAt?.let { (it - now).coerceAtLeast(0) } ?: remainingMs

    fun start(now: Long): TimerState = if (running || finished) this else copy(endAt = now + remainingMs)

    fun pause(now: Long): TimerState = if (!running) this else copy(remainingMs = remaining(now), endAt = null)

    fun reset(): TimerState = copy(remainingMs = durationMs, endAt = null, finished = false)

    /** "+1 min", also on a finished timer (it starts again with one minute). */
    fun plus(ms: Long, now: Long): TimerState = when {
        finished -> copy(remainingMs = ms, endAt = now + ms, finished = false)
        running -> copy(endAt = endAt!! + ms)
        else -> copy(remainingMs = remainingMs + ms)
    }

    fun finish(): TimerState = copy(remainingMs = 0, endAt = null, finished = true)
}

/** RF-E3: stopwatch. Running when [startedAt] is set; [accumulatedMs] holds the time of earlier runs. */
@Serializable
data class StopwatchState(val startedAt: Long? = null, val accumulatedMs: Long = 0, val laps: List<Long> = emptyList()) {
    val running: Boolean get() = startedAt != null

    fun elapsed(now: Long): Long = accumulatedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun start(now: Long) = if (running) this else copy(startedAt = now)

    fun pause(now: Long) = if (!running) this else copy(accumulatedMs = elapsed(now), startedAt = null)

    /** A lap stores the total elapsed time at that moment; lap durations are differences between consecutive laps. */
    fun lap(now: Long) = if (!running) this else copy(laps = laps + elapsed(now))

    fun reset() = StopwatchState()

    fun lapDurations(): List<Long> = laps.mapIndexed { i, total -> total - (laps.getOrNull(i - 1) ?: 0) }
}

/** Splits milliseconds into hours, minutes, seconds and hundredths for display. */
fun clockParts(ms: Long): List<Long> {
    val total = ms.coerceAtLeast(0)
    return listOf(total / 3_600_000, total / 60_000 % 60, total / 1_000 % 60, total / 10 % 100)
}
