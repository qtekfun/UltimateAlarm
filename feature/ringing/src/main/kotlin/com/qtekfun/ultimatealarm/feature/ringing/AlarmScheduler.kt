package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.time.Instant

/**
 * Registers alarms with the system. Always `setAlarmClock` (02-diseno §4): exact, allowed in Doze, and shown to the user
 * as the next alarm. One entry per alarm id and per purpose (occurrence or snooze): registering again replaces it.
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"
    const val EXTRA_SNOOZE = "snooze"

    /** The autotest (RF-O2) is not a database alarm. */
    const val AUTOTEST_ID = -1L

    fun schedule(context: Context, alarmId: Long, triggerAtMillis: Long, snooze: Boolean = false) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = firePendingIntent(context, alarmId, triggerAtMillis, snooze)
        val what = if (snooze) "snooze" else "occurrence"
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent(context)), operation)
            DiagnosticLog.log(context, "SCHEDULED", "$what at=${Instant.ofEpochMilli(triggerAtMillis)} via=setAlarmClock", alarmId)
        } else {
            // Without exact alarms, an inexact window is still better than nothing; the permission banner flags it.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, FALLBACK_WINDOW_MS, operation)
            DiagnosticLog.log(context, "SCHEDULED", "$what at=${Instant.ofEpochMilli(triggerAtMillis)} via=setWindow (no exact alarm permission)", alarmId)
        }
    }

    fun cancel(context: Context, alarmId: Long, snooze: Boolean = false) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId, 0, snooze))
        DiagnosticLog.log(context, "CANCELLED", if (snooze) "snooze" else "occurrence", alarmId)
    }

    private fun firePendingIntent(context: Context, alarmId: Long, triggerAtMillis: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The data URI makes each alarm and purpose a distinct PendingIntent.
            .setData("ultimatealarm://${if (snooze) "snooze" else "alarm"}/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SCHEDULED_AT, triggerAtMillis)
            .putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showPendingIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val ACTION_FIRE = "com.qtekfun.ultimatealarm.action.FIRE"
    private const val FALLBACK_WINDOW_MS = 60_000L
}
