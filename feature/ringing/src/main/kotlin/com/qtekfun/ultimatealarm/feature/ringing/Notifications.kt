package com.qtekfun.ultimatealarm.feature.ringing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.qtekfun.ultimatealarm.core.data.Alarm
import java.time.ZoneId
import java.util.Date

/** Notifications around alarms that are not the ringing one: missed one-shots and the undo after a one-shot is deleted. */
internal object Notifications {
    private const val CHANNEL_EVENTS = "alarm_events"
    private const val UNDO_TIMEOUT_MS = 10_000L
    private const val MISSED_BASE = 100_000
    private const val DELETED_BASE = 200_000

    fun missed(context: Context, alarm: Alarm) {
        val text = context.getString(R.string.missed_text, label(context, alarm), time(context, alarm))
        post(context, MISSED_BASE + alarm.id.toInt(), builder(context).setContentTitle(context.getString(R.string.missed_title)).setContentText(text))
    }

    /** §5.6: a dismissed one-shot is deleted with a 10 s undo. */
    fun deleted(context: Context, alarm: Alarm) {
        val undo = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, UndoDeleteReceiver::class.java).putExtra(UndoDeleteReceiver.EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.deleted_title, label(context, alarm)))
            .setTimeoutAfter(UNDO_TIMEOUT_MS)
            .addAction(0, context.getString(R.string.undo), undo)
        post(context, DELETED_BASE + alarm.id.toInt(), notification)
    }

    fun cancelDeleted(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(DELETED_BASE + alarmId.toInt())
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Without the notification permission there is nothing to show; the permission banner covers it.
        if (manager.areNotificationsEnabled()) manager.notify(id, builder.build())
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_EVENTS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.events_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun launchIntent(context: Context): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun label(context: Context, alarm: Alarm): String = alarm.label.ifBlank { context.getString(R.string.alarm_default_label) }

    private fun time(context: Context, alarm: Alarm): String {
        val day = alarm.oneShotDate ?: java.time.LocalDate.now()
        val millis = day.atTime(alarm.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return DateFormat.getTimeFormat(context).format(Date(millis))
    }
}
