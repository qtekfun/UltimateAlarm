package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.core.content.edit

/**
 * Small device-protected state of the firing path: the pending autotest, the alarms currently registered with the
 * system (for the watchdog) and the spike's FGS override. Readable before the first unlock.
 */
class DiagnosticsStore(context: Context) {
    private val prefs = DeviceStorage.context(context).getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    /** Epoch millis of the pending autotest, or 0 when none is pending. */
    var autotestAt: Long
        get() = prefs.getLong(KEY_AUTOTEST_AT, 0)
        set(value) = prefs.edit { putLong(KEY_AUTOTEST_AT, value) }

    /** Force the mediaPlayback foreground service type instead of systemExempted (diagnostics). */
    var forceMediaPlayback: Boolean
        get() = prefs.getBoolean(KEY_FORCE_MEDIA, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCE_MEDIA, value) }

    /** Alarm id → epoch millis registered with AlarmManager by the last re-schedule. */
    var registered: Map<Long, Long>
        get() = prefs.getString(KEY_REGISTERED, "").orEmpty().split(',').mapNotNull { entry ->
            val (id, at) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (at.toLongOrNull() ?: return@mapNotNull null)
        }.toMap()
        set(value) = prefs.edit { putString(KEY_REGISTERED, value.entries.joinToString(",") { "${it.key}=${it.value}" }) }

    private companion object {
        const val KEY_AUTOTEST_AT = "autotest_at"
        const val KEY_FORCE_MEDIA = "force_media_playback"
        const val KEY_REGISTERED = "registered"
    }
}
