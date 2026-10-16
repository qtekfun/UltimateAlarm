package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import java.util.Date

/** RF-E1: a gentle notification some hours before the first alarm of the next day. Auxiliary task (§4). */
object BedtimeReminder {
    private const val CHANNEL = "bedtime"
    private const val NOTIFICATION_ID = 800_000
    private const val EXTRA_ALARM_AT = "alarm_at"
    private const val WINDOW_MS = 15 * 60_000L

    fun schedule(context: Context, bedtime: Bedtime?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, BedtimeReceiver::class.java).setData("ultimatealarm://bedtime".toUri()).putExtra(EXTRA_ALARM_AT, bedtime?.alarmAt ?: 0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        when {
            bedtime == null -> manager.cancel(operation)
            manager.canScheduleExactAlarms() -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bedtime.at, operation)
            else -> manager.setWindow(AlarmManager.RTC_WAKEUP, bedtime.at, WINDOW_MS, operation)
        }
    }

    internal fun show(context: Context, alarmAt: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || alarmAt <= System.currentTimeMillis()) return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.bedtime_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.bedtime_title))
            .setContentText(context.getString(R.string.bedtime_text, DateFormat.getTimeFormat(context).format(Date(alarmAt))))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(alarmAt - System.currentTimeMillis())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    internal fun alarmAt(intent: Intent) = intent.getLongExtra(EXTRA_ALARM_AT, 0)
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BedtimeReminder.show(context, BedtimeReminder.alarmAt(intent))
        doAsync { Rescheduler.rescheduleAll(context, "bedtime") }
    }
}
