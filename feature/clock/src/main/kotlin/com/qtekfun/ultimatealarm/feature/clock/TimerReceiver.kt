package com.qtekfun.ultimatealarm.feature.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog

/** Timer end (exact alarm) and the notification actions. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Timers.EXTRA_TIMER_ID, 0)
        when (intent.action) {
            Timers.ACTION_FIRE -> {
                // Foreground service first: ringing must not wait for anything else.
                context.startForegroundService(TimerService.ringIntent(context, id))
                DiagnosticLog.log(context, "TIMER_FIRED", "id=$id")
                Timers.finish(context, id)
            }
            Timers.ACTION_PAUSE -> Timers.pause(context, id)
            Timers.ACTION_RESUME -> Timers.resume(context, id)
            Timers.ACTION_RESET -> Timers.reset(context, id)
            Timers.ACTION_PLUS_MINUTE -> Timers.plusMinute(context, id)
        }
    }
}

/** Running timers and the stopwatch notification come back after a reboot or a time change. */
class ClockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timers.syncAll(context)
        Stopwatch.refreshNotification(context)
    }
}
