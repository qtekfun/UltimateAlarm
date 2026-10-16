package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

/** Settings → Holidays (RF-F1, RF-F7): where the user lives, the extra public calendar, the evening notice. */
class HolidaySettings(context: Context) {
    private val prefs = DeviceStorage.context(context).getSharedPreferences("holidays", Context.MODE_PRIVATE)

    /** Automatic holidays from Nager.Date for [country] (and [subdivision]). Off: only manual and ICS holidays. */
    var automatic: Boolean
        get() = prefs.getBoolean(KEY_AUTOMATIC, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOMATIC, value) }

    /** ISO 3166-1 alpha-2, Spain by default (RF-F1). */
    var country: String
        get() = prefs.getString(KEY_COUNTRY, DEFAULT_COUNTRY) ?: DEFAULT_COUNTRY
        set(value) = prefs.edit { putString(KEY_COUNTRY, value) }

    /** ISO 3166-2 (e.g. ES-MD), or `null` for national holidays only. */
    var subdivision: String?
        get() = prefs.getString(KEY_SUBDIVISION, null)
        set(value) = prefs.edit { putString(KEY_SUBDIVISION, value) }

    /** A public calendar (ICS) with extra holidays, e.g. the local ones of a town; `null` = none. */
    var icsUrl: String?
        get() = prefs.getString(KEY_ICS, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_ICS, value?.trim()) }

    /** RF-F7: the evening notice before a holiday. */
    var eveEnabled: Boolean
        get() = prefs.getBoolean(KEY_EVE, true)
        set(value) = prefs.edit { putBoolean(KEY_EVE, value) }

    var eveTime: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt(KEY_EVE_TIME, DEFAULT_EVE.toSecondOfDay()).toLong())
        set(value) = prefs.edit { putInt(KEY_EVE_TIME, value.toSecondOfDay()) }

    var lastRefresh: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_REFRESH, value) }

    /** Last refresh error, shown in Settings; `null` when the last refresh worked. */
    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit { putString(KEY_LAST_ERROR, value) }

    private companion object {
        const val DEFAULT_COUNTRY = "ES"
        val DEFAULT_EVE: LocalTime = LocalTime.of(20, 0)
        const val KEY_AUTOMATIC = "automatic"
        const val KEY_COUNTRY = "country"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_ICS = "ics_url"
        const val KEY_EVE = "eve_enabled"
        const val KEY_EVE_TIME = "eve_time"
        const val KEY_LAST_REFRESH = "last_refresh"
        const val KEY_LAST_ERROR = "last_error"
    }
}
