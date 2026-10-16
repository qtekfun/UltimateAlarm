package com.qtekfun.ultimatealarm.feature.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.DiagnosticsStore
import java.time.LocalDate

/**
 * AlarmManager fired. Starts the ringing service first (an alarm-clock broadcast may start a foreground service from
 * the background), then, off the main thread, records the firing and registers the next occurrence so tomorrow's alarm
 * exists even if this one is never dismissed.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firedAt = System.currentTimeMillis()
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmScheduler.AUTOTEST_ID)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0)
        val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)

        context.startForegroundService(RingingService.startIntent(context, alarmId, firedAt))

        val delay = if (scheduledAt > 0) "${firedAt - scheduledAt}ms" else "unknown"
        DiagnosticLog.log(context, "FIRED", "delay=$delay snooze=$snooze ${deviceState(context)}", alarmId)
        doAsync {
            if (alarmId == AlarmScheduler.AUTOTEST_ID) {
                DiagnosticsStore(context).autotestAt = 0
                DiagnosticLog.log(context, "AUTOTEST_RESULT", "fired delay=$delay", alarmId)
            } else {
                if (!snooze) AlarmGraph.repository(context).markFired(alarmId, LocalDate.now())
                Rescheduler.rescheduleAll(context, "fired")
            }
        }
    }
}
