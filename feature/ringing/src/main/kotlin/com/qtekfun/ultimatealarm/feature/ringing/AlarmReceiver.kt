package com.qtekfun.ultimatealarm.feature.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.TestAlarmStore

/**
 * AlarmManager fired. Starts the ringing service at once (an alarm-clock broadcast may start a foreground service from
 * the background), then logs and schedules the next occurrence so tomorrow's alarm exists even if this one is never
 * dismissed.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firedAt = System.currentTimeMillis()
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, SpikeAlarm.TEST.id)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0)

        context.startForegroundService(RingingService.startIntent(context, alarmId, firedAt))

        val delay = if (scheduledAt > 0) "${firedAt - scheduledAt}ms" else "unknown"
        DiagnosticLog.log(context, "FIRED", "delay=$delay ${deviceState(context)}", alarmId)
        when (SpikeAlarm.of(alarmId)) {
            SpikeAlarm.TEST -> TestAlarms.scheduleNextTest(context)
            SpikeAlarm.AUTOTEST -> {
                TestAlarmStore(context).autotestAt = 0
                DiagnosticLog.log(context, "AUTOTEST_RESULT", "fired delay=$delay", alarmId)
            }
            SpikeAlarm.SNOOZE, null -> Unit
        }
    }
}
