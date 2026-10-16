package com.qtekfun.ultimatealarm.feature.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** RF-E3: stopwatch operations; an ongoing notification with a live count-up while it runs, so closing the app is fine. */
object Stopwatch {
    private const val CHANNEL = "stopwatch"
    private const val NOTIFICATION_ID = 700_000
    internal const val ACTION_TOGGLE = "com.qtekfun.ultimatealarm.stopwatch.TOGGLE"
    internal const val ACTION_LAP = "com.qtekfun.ultimatealarm.stopwatch.LAP"

    fun toggle(context: Context) = change(context) { if (it.running) it.pause(now()) else it.start(now()) }

    fun lap(context: Context) = change(context) { it.lap(now()) }

    fun reset(context: Context) = change(context) { it.reset() }

    private fun change(context: Context, transform: (StopwatchState) -> StopwatchState) {
        ClockStore.get(context).updateStopwatch(transform)
        refreshNotification(context)
    }

    fun refreshNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val state = ClockStore.get(context).stopwatch.value
        if (state.startedAt == null && state.accumulatedMs == 0L) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.stopwatch_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.stopwatch_title))
            .setOngoing(state.running)
            .setSilent(true)
            .setContentIntent(
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            )
            .addAction(0, context.getString(if (state.running) R.string.timer_pause else R.string.timer_resume), action(context, ACTION_TOGGLE))
        if (state.running) {
            builder.setUsesChronometer(true).setWhen(now() - state.elapsed(now())).setShowWhen(true)
                .addAction(0, context.getString(R.string.stopwatch_lap), action(context, ACTION_LAP))
        } else {
            builder.setContentText(formatDuration(state.accumulatedMs))
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun action(context: Context, action: String) = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, StopwatchReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun now() = System.currentTimeMillis()
}

class StopwatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Stopwatch.ACTION_TOGGLE -> Stopwatch.toggle(context)
            Stopwatch.ACTION_LAP -> Stopwatch.lap(context)
        }
    }
}
