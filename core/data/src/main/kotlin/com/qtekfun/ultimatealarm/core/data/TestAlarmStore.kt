package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

/**
 * The spike's single test alarm and the autotest, in device-protected preferences so they are readable when the phone
 * reboots and nobody has unlocked it yet. Replaced by Room + a device-protected copy in Phase 3.
 */
class TestAlarmStore(context: Context) {
    private val prefs = DeviceStorage.context(context).getSharedPreferences("test_alarm", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var time: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt(KEY_TIME, DEFAULT_TIME.toSecondOfDay()).toLong())
        set(value) = prefs.edit { putInt(KEY_TIME, value.toSecondOfDay()) }

    /** Epoch millis the test alarm is currently registered for in AlarmManager, or 0. */
    var scheduledAt: Long
        get() = prefs.getLong(KEY_SCHEDULED_AT, 0)
        set(value) = prefs.edit { putLong(KEY_SCHEDULED_AT, value) }

    /** Epoch millis of the pending autotest, or 0 when none is pending. */
    var autotestAt: Long
        get() = prefs.getLong(KEY_AUTOTEST_AT, 0)
        set(value) = prefs.edit { putLong(KEY_AUTOTEST_AT, value) }

    /** Force the mediaPlayback foreground service type instead of systemExempted (spike comparison). */
    var forceMediaPlayback: Boolean
        get() = prefs.getBoolean(KEY_FORCE_MEDIA, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCE_MEDIA, value) }

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)
        const val KEY_ENABLED = "enabled"
        const val KEY_TIME = "time"
        const val KEY_SCHEDULED_AT = "scheduled_at"
        const val KEY_AUTOTEST_AT = "autotest_at"
        const val KEY_FORCE_MEDIA = "force_media_playback"
    }
}
