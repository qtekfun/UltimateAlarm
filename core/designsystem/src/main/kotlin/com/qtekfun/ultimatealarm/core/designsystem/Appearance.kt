package com.qtekfun.ultimatealarm.core.designsystem

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The accent colours of the "Amanecer" look; [DYNAMIC] uses the wallpaper's Material You palette instead. */
enum class AccentColor(val rgb: Int) {
    ORANGE(0xC2410C),
    RASPBERRY(0xB4235A),
    PLUM(0x7E22CE),
    INDIGO(0x3B4A8C),
    TEAL(0x0F766E),
    MUSTARD(0xA16207),
    DYNAMIC(0xC2410C)
}

data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: AccentColor = AccentColor.ORANGE)

/**
 * The chosen look, kept in device-protected preferences (the ringing screen can show before the first unlock) and held
 * as Compose state so a change in Settings repaints every open screen at once.
 */
object AppearanceStore {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_ACCENT = "accent"

    private var loaded = false
    private var state by mutableStateOf(Appearance())

    fun current(context: Context): Appearance {
        if (!loaded) {
            val prefs = prefs(context)
            state = Appearance(
                mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM,
                accent = AccentColor.entries.firstOrNull { it.name == prefs.getString(KEY_ACCENT, null) } ?: AccentColor.ORANGE
            )
            loaded = true
        }
        return state
    }

    fun set(context: Context, appearance: Appearance) {
        prefs(context).edit {
            putString(KEY_MODE, appearance.mode.name)
            putString(KEY_ACCENT, appearance.accent.name)
        }
        state = appearance
        loaded = true
    }

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
