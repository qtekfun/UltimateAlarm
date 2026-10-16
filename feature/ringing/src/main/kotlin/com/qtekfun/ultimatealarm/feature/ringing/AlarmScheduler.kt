package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.time.Instant

/** Why an alarm is registered with the system. Each alarm has at most one entry per purpose. */
enum class AlarmPurpose(val path: String) {
    /** The next occurrence (02-diseno §4). */
    OCCURRENCE("alarm"),

    /** The end of a snooze (RF-R3). */
    SNOOZE("snooze"),

    /** Safety net of the backup tone in case the ringing service died (§5.5). */
    BACKUP("backup"),

    /** Resume a ring interrupted by a reboot (§5.4). */
    RESUME("resume")
}

/**
 * Registers alarms with the system. Always `setAlarmClock` (02-diseno §4): exact, allowed in Doze, shown to the user as
 * the next alarm, and it lets the receiver start the ringing foreground service from the background.
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"
    const val EXTRA_PURPOSE = "purpose"

    /** The autotest (RF-O2) is not a database alarm. */
    const val AUTOTEST_ID = -1L

    fun schedule(context: Context, alarmId: Long, triggerAtMillis: Long, purpose: AlarmPurpose = AlarmPurpose.OCCURRENCE) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = firePendingIntent(context, alarmId, triggerAtMillis, purpose)
        val what = purpose.path
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent(context)), operation)
            DiagnosticLog.log(context, "SCHEDULED", "$what at=${Instant.ofEpochMilli(triggerAtMillis)} via=setAlarmClock", alarmId)
        } else {
            // Without exact alarms, an inexact window is still better than nothing; the permission banner flags it.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, FALLBACK_WINDOW_MS, operation)
            DiagnosticLog.log(context, "SCHEDULED", "$what at=${Instant.ofEpochMilli(triggerAtMillis)} via=setWindow (no exact alarm permission)", alarmId)
        }
    }

    fun cancel(context: Context, alarmId: Long, purpose: AlarmPurpose = AlarmPurpose.OCCURRENCE) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId, 0, purpose))
        DiagnosticLog.log(context, "CANCELLED", purpose.path, alarmId)
    }

    private fun firePendingIntent(context: Context, alarmId: Long, triggerAtMillis: Long, purpose: AlarmPurpose): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The data URI makes each alarm and purpose a distinct PendingIntent.
            .setData("ultimatealarm://${purpose.path}/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SCHEDULED_AT, triggerAtMillis)
            .putExtra(EXTRA_PURPOSE, purpose.name)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showPendingIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val ACTION_FIRE = "com.qtekfun.ultimatealarm.action.FIRE"
    private const val FALLBACK_WINDOW_MS = 60_000L
}
