package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.time.Instant

/** The spike's alarms. Real alarms use their database ids from Phase 3. */
enum class SpikeAlarm(val id: Long) {
    TEST(1),
    AUTOTEST(2),
    SNOOZE(3);

    companion object {
        fun of(id: Long): SpikeAlarm? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Registers alarms with the system. Always `setAlarmClock` (02-diseno §4): exact, allowed in Doze, and shown to the
 * user as the next alarm. One entry per alarm id: scheduling again replaces it.
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    fun schedule(context: Context, alarmId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = firePendingIntent(context, alarmId, triggerAtMillis)
        if (alarmManager.canScheduleExactAlarms()) {
            val show = showPendingIntent(context)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
            DiagnosticLog.log(context, "SCHEDULED", "at=${Instant.ofEpochMilli(triggerAtMillis)} via=setAlarmClock", alarmId)
        } else {
            // Without exact alarms, an inexact window is still better than nothing; the diagnostics screen flags it.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, FALLBACK_WINDOW_MS, operation)
            DiagnosticLog.log(context, "SCHEDULED", "at=${Instant.ofEpochMilli(triggerAtMillis)} via=setWindow (no exact alarm permission)", alarmId)
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(firePendingIntent(context, alarmId, 0))
        DiagnosticLog.log(context, "CANCELLED", "", alarmId)
    }

    private fun firePendingIntent(context: Context, alarmId: Long, triggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The data URI makes each alarm id a distinct PendingIntent.
            .setData("ultimatealarm://alarm/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SCHEDULED_AT, triggerAtMillis)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showPendingIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val ACTION_FIRE = "com.qtekfun.ultimatealarm.action.FIRE"
    private const val FALLBACK_WINDOW_MS = 60_000L
}
