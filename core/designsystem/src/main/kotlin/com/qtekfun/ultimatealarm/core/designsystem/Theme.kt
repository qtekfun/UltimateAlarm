package com.qtekfun.ultimatealarm.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive. Dynamic colour (the wallpaper's Material You palette) is the default, as minSdk 31 always has it;
 * [dynamicColor] = false falls back to the brand colours (night blue and sunrise amber).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UltimateAlarmTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        else -> brandColorScheme(darkTheme)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private fun brandColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFB4C5FF),
        onPrimary = Color(0xFF13296B),
        secondary = Color(0xFFFFB870),
        onSecondary = Color(0xFF4A2800)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF3A53A4),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8A5100),
        onSecondary = Color(0xFFFFFFFF)
    )
}
