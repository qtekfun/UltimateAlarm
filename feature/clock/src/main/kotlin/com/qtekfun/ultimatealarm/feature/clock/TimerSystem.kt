package com.qtekfun.ultimatealarm.feature.clock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog

/** The system side of timers: the exact alarm of a running timer and its ongoing notification. */
internal object TimerSystem {
    private const val CHANNEL = "timers"
    private const val NOTIFICATION_BASE = 600_000
    private const val MINUTE_MS = 60_000L

    fun scheduleAlarm(context: Context, timer: TimerState) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = timer.endAt ?: return
        val operation = fireIntent(context, timer.id)
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, MINUTE_MS, operation)
        }
        DiagnosticLog.log(context, "TIMER_SCHEDULED", "id=${timer.id} in=${(at - now()) / 1_000}s")
    }

    fun cancelAlarm(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context, id))

    private fun fireIntent(context: Context, id: Long) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(
            context,
            TimerReceiver::class.java
        ).setAction(Timers.ACTION_FIRE).setData("ultimatealarm://timer/$id".toUri()).putExtra(Timers.EXTRA_TIMER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(context: Context, action: String, id: Long) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerReceiver::class.java).setAction(action).setData("ultimatealarm://timer/$id/$action".toUri()).putExtra(Timers.EXTRA_TIMER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Ongoing notification with a live countdown (the system draws it, nothing runs in the app meanwhile). */
    fun post(context: Context, timer: TimerState) {
        val manager = notifications(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(timer.label.ifBlank { context.getString(R.string.timer_default_label) })
            .setOngoing(timer.running)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            )
        if (timer.running) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(timer.endAt!!).setShowWhen(true)
                .addAction(0, context.getString(R.string.timer_pause), actionIntent(context, Timers.ACTION_PAUSE, timer.id))
                .addAction(0, context.getString(R.string.timer_plus_minute), actionIntent(context, Timers.ACTION_PLUS_MINUTE, timer.id))
        } else {
            builder.setContentText(context.getString(R.string.timer_paused, formatDuration(timer.remainingMs)))
                .addAction(0, context.getString(R.string.timer_resume), actionIntent(context, Timers.ACTION_RESUME, timer.id))
                .addAction(0, context.getString(R.string.timer_reset), actionIntent(context, Timers.ACTION_RESET, timer.id))
        }
        manager.notify(NOTIFICATION_BASE + timer.id.toInt(), builder.build())
    }

    private fun ensureChannel(context: Context) {
        val manager = notifications(context)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun cancelNotification(context: Context, id: Long) = notifications(context).cancel(NOTIFICATION_BASE + id.toInt())

    private fun now() = System.currentTimeMillis()
}
