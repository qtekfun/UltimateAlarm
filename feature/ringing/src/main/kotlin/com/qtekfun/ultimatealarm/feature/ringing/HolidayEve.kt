package com.qtekfun.ultimatealarm.feature.ringing

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.scheduling.SchedulingEngine
import java.time.LocalDate

/**
 * RF-F7: the evening before a holiday, "Tomorrow is a holiday (Name). You have N alarms." with **Skip them** and
 * **Keep them**. Without an answer the alarms ring. Auxiliary task (§4): exact-and-allow-while-idle, or a 15 min window;
 * it never touches the alarms themselves until the user answers.
 */
object HolidayEve {
    private const val EXTRA_DATE = "date"
    private const val EXTRA_SKIP = "skip"
    private const val CHANNEL = "holiday_eve"
    private const val NOTIFICATION_ID = 500_000
    private const val WINDOW_MS = 15 * 60_000L

    fun schedule(context: Context, notice: EveNotice?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = eveIntent(context, notice?.holidayDate)
        if (notice == null) {
            manager.cancel(operation)
            return
        }
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notice.at, operation)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, notice.at, WINDOW_MS, operation)
        }
    }

    private fun eveIntent(context: Context, date: LocalDate?): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HolidayEveReceiver::class.java).setData("ultimatealarm://eve".toUri()).putExtra(EXTRA_DATE, date?.toEpochDay() ?: 0),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Re-checks at notification time: alarms or answers may have changed since it was scheduled. */
    internal suspend fun show(context: Context, date: LocalDate) {
        val repository = AlarmGraph.repository(context)
        val alarms = repository.alarms()
        val eve = SchedulingEngine.holidayEve(alarms.map { it.spec }, repository.rules(), date)
        if (eve == null) {
            DiagnosticLog.log(context, "HOLIDAY_EVE", "nothing to ask for $date")
            return
        }
        val count = eve.alarmIds.size
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.eve_title, eve.holiday.name.ifBlank { context.getString(R.string.eve_unnamed) }))
            .setContentText(context.resources.getQuantityString(R.plurals.eve_text, count, count))
            .addAction(0, context.getString(R.string.eve_skip_all), answerIntent(context, date, skip = true))
            .addAction(0, context.getString(R.string.eve_keep), answerIntent(context, date, skip = false))
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
        DiagnosticLog.log(context, "HOLIDAY_EVE", "asked for $date (${eve.holiday.name}), alarms=${eve.alarmIds}")
    }

    internal suspend fun answer(context: Context, date: LocalDate, skip: Boolean) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        AlarmGraph.repository(context).decideHoliday(date, null, skip)
        DiagnosticLog.log(context, "HOLIDAY_DECISION", "date=$date skip=$skip")
    }

    private fun answerIntent(context: Context, date: LocalDate, skip: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (skip) 1 else 2,
        Intent(context, HolidayAnswerReceiver::class.java)
            .setData("ultimatealarm://eve/${if (skip) "skip" else "keep"}".toUri())
            .putExtra(EXTRA_DATE, date.toEpochDay())
            .putExtra(EXTRA_SKIP, skip),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun builder(context: Context): NotificationCompat.Builder {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.eve_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
    }

    internal fun date(intent: Intent): LocalDate? = intent.getLongExtra(EXTRA_DATE, 0).takeIf { it != 0L }?.let(LocalDate::ofEpochDay)

    internal fun skip(intent: Intent) = intent.getBooleanExtra(EXTRA_SKIP, false)
}

class HolidayEveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = HolidayEve.date(intent) ?: return
        doAsync {
            HolidayEve.show(context, date)
            // Plan the next evening notice.
            Rescheduler.rescheduleAll(context, "holiday_eve")
        }
    }
}

class HolidayAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = HolidayEve.date(intent) ?: return
        doAsync { HolidayEve.answer(context, date, HolidayEve.skip(intent)) }
    }
}
