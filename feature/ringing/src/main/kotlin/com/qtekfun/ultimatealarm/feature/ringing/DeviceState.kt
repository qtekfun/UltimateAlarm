package com.qtekfun.ultimatealarm.feature.ringing

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.UserManager

/** One line describing the conditions an alarm fired under, for the event log (spike scenarios). */
internal fun deviceState(context: Context): String {
    val power = context.getSystemService(PowerManager::class.java)
    val notifications = context.getSystemService(NotificationManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val user = context.getSystemService(UserManager::class.java)
    val dnd = when (notifications.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms_only"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "total_silence"
        else -> "unknown"
    }
    return listOf(
        "unlocked=${user.isUserUnlocked}",
        "interactive=${power.isInteractive}",
        "doze=${power.isDeviceIdleMode}",
        "power_save=${power.isPowerSaveMode}",
        "ignoring_battery_opt=${power.isIgnoringBatteryOptimizations(context.packageName)}",
        "dnd=$dnd",
        "alarm_volume=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"
    ).joinToString(" ")
}
