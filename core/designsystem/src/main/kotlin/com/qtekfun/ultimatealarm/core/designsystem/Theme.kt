package com.qtekfun.ultimatealarm.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive with the "Amanecer" look (D-19): warm ground, Bricolage Grotesque times, generous radii. The mode
 * (system, light, dark) and the accent come from Settings → Appearance; [darkTheme] forces a mode (full-screen alarm and
 * timer screens are always dark). The accent [AccentColor.DYNAMIC] uses the wallpaper's Material You palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UltimateAlarmTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance = AppearanceStore.current(context)
    val dark = darkTheme ?: when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        appearance.accent == AccentColor.DYNAMIC && dark -> dynamicDarkColorScheme(context)
        appearance.accent == AccentColor.DYNAMIC -> dynamicLightColorScheme(context)
        else -> AmanecerPalette.scheme(dark, appearance.accent.rgb)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = amanecerTypography(),
        shapes = AmanecerShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private val AmanecerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Whether this scheme is a dark one (screens pick card tones by it). */
val ColorScheme.isDark: Boolean get() = background.luminance() < HALF

/** Raised cards: white on the light ground, one step up from the ground in the dark. */
val ColorScheme.card: Color get() = if (isDark) surfaceContainer else surfaceContainerLowest

private const val HALF = 0.5f
