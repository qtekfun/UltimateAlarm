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

/** h:mm:ss.cc or m:ss.cc, for elapsed times (stopwatch, laps): rounded down. */
fun formatElapsed(ms: Long): String {
    val parts = clockParts(ms)
    val (h, m, s) = parts
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, parts[3]) else "%d:%02d.%02d".format(m, s, parts[3])
}

/**
 * RF-E2: the timer keypad. Digits typed from the right fill hh mm ss, as on a microwave ("130" is 1 min 30 s); leading
 * zeros are dropped and keys beyond six digits ignored. Minutes or seconds over 59 count as they read ("90" s is 1:30).
 */
object TimerKeypad {
    const val MAX_DIGITS = 6

    fun type(digits: String, key: String): String = key.fold(digits) { typed, digit ->
        when {
            !digit.isDigit() || typed.length >= MAX_DIGITS -> typed
            typed.isEmpty() && digit == '0' -> typed
            else -> typed + digit
        }
    }

    fun delete(digits: String): String = digits.dropLast(1)

    /** Hours, minutes and seconds as typed. */
    fun parts(digits: String): List<Int> = digits.padStart(MAX_DIGITS, '0').chunked(2).map(String::toInt)

    fun durationMs(digits: String): Long {
        val (h, m, s) = parts(digits)
        return (h * 3_600L + m * 60L + s) * 1_000L
    }
}
