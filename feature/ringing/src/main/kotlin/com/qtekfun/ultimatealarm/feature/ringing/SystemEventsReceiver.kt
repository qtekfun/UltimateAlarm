package com.qtekfun.ultimatealarm.feature.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager forgets everything on reboot and keeps wall-clock alarms at their old instant across time and zone
 * changes: every one of these events re-registers the alarms (02-diseno §4). LOCKED_BOOT_COMPLETED arrives before the
 * first unlock, so the alarms are back even if nobody unlocks the phone.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> "locked_boot"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_TIME_CHANGED -> "time_set"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "package_replaced"
            else -> return
        }
        doAsync {
            Rescheduler.rescheduleAll(context, reason)
            Watchdog.ensureScheduled(context)
        }
    }
}
