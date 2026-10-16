package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.time.LocalDate
import java.util.Date

/**
 * RF-R6: "Alarm in 1 h" with "Skip this". Auxiliary task (§4): `setExactAndAllowWhileIdle` when exact alarms are allowed,
 * otherwise a 15 min window. It never touches the alarm itself; if it fails, the alarm still rings.
 */
object PreNotices {
    private const val EXTRA_ALARM_ID = "alarm_id"
    private const val EXTRA_DATE = "date"
    private const val EXTRA_OCCURRENCE_AT = "occurrence_at"
    private const val WINDOW_MS = 15 * 60_000L
    private const val NOTIFICATION_BASE = 400_000

    fun schedule(context: Context, alarmId: Long, notice: PreNotice) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context, alarmId, notice)
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notice.at, operation)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, notice.at, WINDOW_MS, operation)
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alarmId, null))
    }

    fun dismissNotification(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + alarmId.toInt())
    }

    private fun pendingIntent(context: Context, alarmId: Long, notice: PreNotice?): PendingIntent {
        val intent = Intent(context, PreNoticeReceiver::class.java)
            .setData("ultimatealarm://prenotice/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId)
        notice?.let { intent.putExtra(EXTRA_DATE, it.occurrenceDate.toEpochDay()).putExtra(EXTRA_OCCURRENCE_AT, it.occurrenceAt) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun show(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        val occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, 0)
        val skip = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, SkipThisReceiver::class.java)
                .setData("ultimatealarm://skipthis/$alarmId".toUri())
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_DATE, date.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val time = DateFormat.getTimeFormat(context).format(Date(occurrenceAt))
        val notification = Notifications.builder(context)
            .setContentTitle(context.getString(R.string.pre_notice_title, time))
            .setTimeoutAfter((occurrenceAt - System.currentTimeMillis()).coerceAtLeast(1))
            .addAction(0, context.getString(R.string.pre_notice_skip), skip)
        Notifications.post(context, NOTIFICATION_BASE + alarmId.toInt(), notification)
        DiagnosticLog.log(context, "PRE_NOTICE", "for $date", alarmId)
    }

    internal fun skip(context: Context, intent: Intent, receiver: BroadcastReceiver) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        dismissNotification(context, alarmId)
        receiver.doAsync {
            AlarmGraph.repository(context).skip(alarmId, date)
            DiagnosticLog.log(context, "SKIPPED", "from the 1 h notice, $date", alarmId)
        }
    }
}

class PreNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PreNotices.show(context, intent)
}

class SkipThisReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PreNotices.skip(context, intent, this)
}
