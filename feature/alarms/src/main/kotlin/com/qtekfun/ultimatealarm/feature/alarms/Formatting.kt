package com.qtekfun.ultimatealarm.feature.alarms

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.scheduling.OffReason
import com.qtekfun.ultimatealarm.core.scheduling.Suppression
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Wall-clock time in the system's 12/24 h format (RNF-6). */
fun formatTime(context: Context, time: LocalTime): String {
    val millis = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateFormat.getTimeFormat(context).format(Date(millis))
}

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

fun formatDay(day: DayOfWeek, style: TextStyle = TextStyle.SHORT): String = day.getDisplayName(style, Locale.getDefault())

fun formatLongDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
fun scheduleText(alarm: Alarm): String = when (val summary = summarize(alarm)) {
    ScheduleSummary.EveryDay -> stringResource(R.string.schedule_every_day)
    ScheduleSummary.Weekdays -> stringResource(R.string.schedule_weekdays)
    ScheduleSummary.Weekend -> stringResource(R.string.schedule_weekend)
    ScheduleSummary.NoDays -> stringResource(R.string.schedule_no_days)
    is ScheduleSummary.Days -> summary.days.joinToString(", ") { formatDay(it) }
    is ScheduleSummary.Cycle -> stringResource(R.string.schedule_cycle, summary.ringing, summary.length)
    is ScheduleSummary.Once -> stringResource(R.string.schedule_once, formatDate(summary.date))
    ScheduleSummary.NextTime -> stringResource(R.string.schedule_next_time)
}

@Composable
fun ringsInText(duration: Duration): String {
    val (hours, minutes) = hoursAndMinutes(duration)
    val days = hours / HOURS_PER_DAY
    return when {
        days > 0 -> pluralStringResource(R.plurals.rings_in_days, days.toInt(), days.toInt(), (hours % HOURS_PER_DAY).toInt())
        hours > 0 -> stringResource(R.string.rings_in_hours, hours.toInt(), minutes.toInt())
        else -> pluralStringResource(R.plurals.rings_in_minutes, minutes.toInt(), minutes.toInt())
    }
}

/** RF-A2 / RF-P7: why the alarm does not ring on a day it would normally ring. */
@Composable
fun suppressionText(suppression: Suppression): String {
    val day = formatDate(suppression.date)
    return when (suppression.reason) {
        OffReason.HOLIDAY -> stringResource(R.string.off_holiday, day, suppression.holiday?.name.orEmpty())
        OffReason.VACATION -> stringResource(R.string.off_vacation, day)
        OffReason.SKIPPED -> stringResource(R.string.off_skipped, day)
        OffReason.EXCEPTION -> stringResource(R.string.off_exception, day)
        OffReason.NOT_SCHEDULED, OffReason.DISABLED -> ""
    }
}

@Composable
fun timeText(time: LocalTime): String = formatTime(LocalContext.current, time)

private const val HOURS_PER_DAY = 24
