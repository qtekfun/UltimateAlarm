package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.core.content.edit

/**
 * The ringing session and the global ringing settings, in device-protected preferences: the service reads them before
 * the first unlock and right after the process was killed (02-diseno §5.4).
 */
class RingStore(context: Context) {
    private val prefs = DeviceStorage.context(context).getSharedPreferences("ringing", Context.MODE_PRIVATE)

    var state: RingState?
        get() {
            val id = prefs.getLong(KEY_ALARM, NONE)
            if (id == NONE) return null
            return RingState(id, prefs.getLong(KEY_FIRED_AT, 0), prefs.getInt(KEY_SNOOZES, 0), prefs.getBoolean(KEY_BACKUP, false))
        }
        set(value) = prefs.edit(commit = true) {
            if (value == null) {
                remove(KEY_ALARM)
                remove(KEY_FIRED_AT)
                remove(KEY_SNOOZES)
                remove(KEY_BACKUP)
            } else {
                putLong(KEY_ALARM, value.alarmId)
                putLong(KEY_FIRED_AT, value.firedAt)
                putInt(KEY_SNOOZES, value.snoozeCount)
                putBoolean(KEY_BACKUP, value.backupFired)
            }
        }

    /** Snoozes used by an alarm that is snoozed now (between rings). */
    fun snoozesOf(alarmId: Long): Int = prefs.getInt("$KEY_SNOOZED_PREFIX$alarmId", 0)

    fun setSnoozes(alarmId: Long, count: Int?) = prefs.edit(commit = true) {
        if (count == null) remove("$KEY_SNOOZED_PREFIX$alarmId") else putInt("$KEY_SNOOZED_PREFIX$alarmId", count)
    }

    /** Snoozed alarms → epoch millis their snooze ends, so a reboot can register them again. */
    val snoozedUntil: Map<Long, Long>
        get() = prefs.all.mapNotNull { (key, value) ->
            val id = key.removePrefix(KEY_UNTIL_PREFIX).takeIf { key.startsWith(KEY_UNTIL_PREFIX) }?.toLongOrNull() ?: return@mapNotNull null
            (value as? Long)?.let { id to it }
        }.toMap()

    fun setSnoozedUntil(alarmId: Long, until: Long?) = prefs.edit(commit = true) {
        if (until == null) remove("$KEY_UNTIL_PREFIX$alarmId") else putLong("$KEY_UNTIL_PREFIX$alarmId", until)
    }

    /** RF-R7: what the volume buttons do while ringing. */
    var volumeButtons: VolumeButtonAction
        get() = VolumeButtonAction.entries.firstOrNull { it.name == prefs.getString(KEY_VOLUME_BUTTONS, null) } ?: VolumeButtonAction.SNOOZE
        set(value) = prefs.edit { putString(KEY_VOLUME_BUTTONS, value.name) }

    /** RF-R1: whether a ring turns the screen on by itself. Off, the alarm rings and its screen waits behind the lock screen. */
    var turnScreenOn: Boolean
        get() = prefs.getBoolean(KEY_TURN_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_TURN_SCREEN_ON, value) }

    /** RF-S5: global backup tone (encoded SoundRef), `null` = the pack's. */
    var backupSound: String?
        get() = prefs.getString(KEY_BACKUP_SOUND, null)
        set(value) = prefs.edit { putString(KEY_BACKUP_SOUND, value) }

    /** RF-E1: bedtime reminder before the first alarm of the next day. */
    var bedtimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEDTIME, true)
        set(value) = prefs.edit { putBoolean(KEY_BEDTIME, value) }

    var bedtimeHours: Int
        get() = prefs.getInt(KEY_BEDTIME_HOURS, DEFAULT_BEDTIME_HOURS)
        set(value) = prefs.edit { putInt(KEY_BEDTIME_HOURS, value) }

    /** RF-R8: minutes after which a ring silences itself; `null` = the default of [RingPolicy.silenceAt]. */
    var silenceAfterMinutes: Int?
        get() = prefs.getInt(KEY_SILENCE, -1).takeIf { it > 0 }
        set(value) = prefs.edit { putInt(KEY_SILENCE, value ?: -1) }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val KEY_ALARM = "ring_alarm"
        const val KEY_FIRED_AT = "ring_fired_at"
        const val KEY_SNOOZES = "ring_snoozes"
        const val KEY_BACKUP = "ring_backup"
        const val KEY_SNOOZED_PREFIX = "snoozed_"
        const val KEY_UNTIL_PREFIX = "snoozed_until_"
        const val KEY_VOLUME_BUTTONS = "volume_buttons"
        const val KEY_TURN_SCREEN_ON = "turn_screen_on"
        const val KEY_SILENCE = "silence_after"
        const val KEY_BACKUP_SOUND = "backup_sound"
        const val KEY_BEDTIME = "bedtime"
        const val KEY_BEDTIME_HOURS = "bedtime_hours"
        const val DEFAULT_BEDTIME_HOURS = 8
    }
}

enum class VolumeButtonAction { SNOOZE, DISMISS, NOTHING }
