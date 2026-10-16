package com.qtekfun.ultimatealarm.feature.ringing

import android.app.NotificationManager
import android.content.Context
import android.text.format.DateFormat
import java.util.Date

internal object SnoozeNotifications {
    private const val SNOOZED_BASE = 300_000

    /**
     * RF-R3: "Snoozed until HH:MM", kept until the snooze ends, with a Dismiss action unless [canDismiss] is false (the
     * alarm has a challenge, which must not be skipped by snoozing).
     */
    fun show(context: Context, alarmId: Long, label: String, until: Long, canDismiss: Boolean = true) {
        val dismiss = RingingService.actionIntent(context, RingingService.ACTION_DISMISS_SNOOZED, alarmId)
        val notification = Notifications.builder(context)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.snoozed_until, DateFormat.getTimeFormat(context).format(Date(until))))
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(until - System.currentTimeMillis())
        if (canDismiss) notification.addAction(0, context.getString(R.string.ringing_dismiss), dismiss)
        Notifications.post(context, SNOOZED_BASE + alarmId.toInt(), notification)
    }

    fun cancel(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(SNOOZED_BASE + alarmId.toInt())
    }
}
