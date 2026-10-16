package com.qtekfun.ultimatealarm.feature.clock

import android.content.Context

/**
 * Timer operations (RF-E2): state in [ClockStore], an exact alarm per running timer, and an ongoing notification with a
 * live countdown per timer. Several timers can run at once.
 */
object Timers {
    const val PRESETS_MINUTES = "1,3,5,10,15,30"
    private const val MINUTE_MS = 60_000L
    internal const val EXTRA_TIMER_ID = "timer_id"
    internal const val ACTION_PAUSE = "com.qtekfun.ultimatealarm.timer.PAUSE"
    internal const val ACTION_RESUME = "com.qtekfun.ultimatealarm.timer.RESUME"
    internal const val ACTION_RESET = "com.qtekfun.ultimatealarm.timer.RESET"
    internal const val ACTION_PLUS_MINUTE = "com.qtekfun.ultimatealarm.timer.PLUS"
    internal const val ACTION_FIRE = "com.qtekfun.ultimatealarm.timer.FIRE"

    fun add(context: Context, durationMs: Long, label: String = "", start: Boolean = true) {
        val store = ClockStore.get(context)
        val timer = TimerState(store.nextTimerId(), label, durationMs).let { if (start) it.start(System.currentTimeMillis()) else it }
        store.updateTimers { it + timer }
        sync(context, timer.id)
    }

    fun pause(context: Context, id: Long) = change(context, id) { it.pause(System.currentTimeMillis()) }

    fun resume(context: Context, id: Long) = change(context, id) { it.start(System.currentTimeMillis()) }

    fun reset(context: Context, id: Long) = change(context, id) { it.reset() }

    fun plusMinute(context: Context, id: Long) = change(context, id) { it.plus(MINUTE_MS, System.currentTimeMillis()) }

    fun delete(context: Context, id: Long) {
        ClockStore.get(context).updateTimers { list -> list.filterNot { it.id == id } }
        TimerSystem.cancelAlarm(context, id)
        TimerSystem.cancelNotification(context, id)
    }

    internal fun finish(context: Context, id: Long) = change(context, id) { it.finish() }

    private fun change(context: Context, id: Long, transform: (TimerState) -> TimerState) {
        ClockStore.get(context).updateTimers { list -> list.map { if (it.id == id) transform(it) else it } }
        sync(context, id)
    }

    /** Re-arms every running timer: after a reboot or a time change. A timer that ended meanwhile rings now. */
    fun syncAll(context: Context) = ClockStore.get(context).timers.value.forEach { sync(context, it.id) }

    private fun sync(context: Context, id: Long) {
        val timer = ClockStore.get(context).timers.value.firstOrNull { it.id == id }
        if (timer == null || !timer.running) {
            TimerSystem.cancelAlarm(context, id)
        } else {
            TimerSystem.scheduleAlarm(context, timer)
        }
        if (timer == null || timer.finished) {
            TimerSystem.cancelNotification(context, id)
        } else {
            TimerSystem.post(context, timer)
        }
    }
}

/** h:mm:ss or m:ss. */
fun formatDuration(ms: Long): String {
    val (h, m, s) = clockParts(ms + MS_ROUND_UP)
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A countdown shows 0:01 until the very end, like any timer. */
private const val MS_ROUND_UP = 999L
