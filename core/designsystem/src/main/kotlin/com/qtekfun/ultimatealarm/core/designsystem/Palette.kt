package com.qtekfun.ultimatealarm.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Colour arithmetic on 0xRRGGBB values; pure so it is unit tested. */
object ColorMath {
    /** [from] moved [amount] (0..1) of the way to [to], per channel. */
    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from shr shift and BYTE
            val b = to shr shift and BYTE
            return Math.round(a + (b - a) * amount).coerceIn(0, BYTE)
        }
        return (channel(RED) shl RED) or (channel(GREEN) shl GREEN) or channel(0)
    }

    /** WCAG relative luminance. */
    fun luminance(rgb: Int): Double {
        fun linear(shift: Int): Double {
            val c = (rgb shr shift and BYTE) / BYTE.toDouble()
            return if (c <= SRGB_KNEE) c / SRGB_LINEAR else Math.pow((c + SRGB_OFFSET) / (1 + SRGB_OFFSET), SRGB_GAMMA)
        }
        return LUM_R * linear(RED) + LUM_G * linear(GREEN) + LUM_B * linear(0)
    }

    /** WCAG contrast ratio between two colours (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
    }

    private const val BYTE = 0xFF
    private const val RED = 16
    private const val GREEN = 8
    private const val SRGB_KNEE = 0.04045
    private const val SRGB_LINEAR = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
    private const val LUM_R = 0.2126
    private const val LUM_G = 0.7152
    private const val LUM_B = 0.0722
    private const val CONTRAST_OFFSET = 0.05
}

/**
 * The "Amanecer" palette (warm ground and one accent of the user's choice) as a Material colour scheme, so every screen
 * picks it up. Secondary and tertiary are deeper and paler shades of the same accent, so every icon and button follows
 * the chosen colour. In the dark the accent is lightened and carries dark text.
 */
object AmanecerPalette {
    fun scheme(dark: Boolean, accent: Int): ColorScheme = if (dark) darkScheme(accent) else lightScheme(accent)

    /** The accent as painted: lighter in the dark so it reads on the dark ground. */
    fun primary(dark: Boolean, accent: Int): Int = if (dark) ColorMath.mix(accent, WHITE, DARK_LIFT) else accent

    private fun lightScheme(accent: Int) = lightColorScheme(
        primary = c(accent),
        onPrimary = c(WHITE),
        primaryContainer = c(ColorMath.mix(accent, WHITE, SOFT)),
        onPrimaryContainer = c(ColorMath.mix(accent, BLACK, INK)),
        inversePrimary = c(ColorMath.mix(accent, WHITE, DARK_LIFT)),
        secondary = c(ColorMath.mix(accent, BLACK, DEEP)),
        onSecondary = c(WHITE),
        secondaryContainer = c(ColorMath.mix(accent, WHITE, PALE)),
        onSecondaryContainer = c(ColorMath.mix(accent, BLACK, DARK_TEXT_MIX)),
        tertiary = c(ColorMath.mix(accent, BLACK, SLIGHT)),
        onTertiary = c(WHITE),
        tertiaryContainer = c(ColorMath.mix(accent, WHITE, PALER)),
        onTertiaryContainer = c(ColorMath.mix(accent, BLACK, DARK_TEXT_MIX)),
        background = c(LIGHT_GROUND),
        onBackground = c(LIGHT_INK),
        surface = c(LIGHT_GROUND),
        onSurface = c(LIGHT_INK),
        surfaceVariant = c(0xF3EEE8),
        onSurfaceVariant = c(0x6B5A48),
        surfaceTint = c(accent),
        inverseSurface = c(0x382E24),
        inverseOnSurface = c(0xFBEFE3),
        outline = c(0x8A7A69),
        outlineVariant = c(0xE2D5C5),
        surfaceBright = c(LIGHT_GROUND),
        surfaceDim = c(0xE8DCCD),
        surfaceContainerLowest = c(WHITE),
        surfaceContainerLow = c(0xFFFCF8),
        surfaceContainer = c(0xF6EADB),
        surfaceContainerHigh = c(0xF1E2CF),
        surfaceContainerHighest = c(0xEBDAC4)
    )

    private fun darkScheme(accent: Int): ColorScheme {
        val primary = primary(true, accent)
        return darkColorScheme(
            primary = c(primary),
            onPrimary = c(DARK_ON_PRIMARY),
            primaryContainer = c(ColorMath.mix(accent, DARK_GROUND, DARK_SOFT)),
            onPrimaryContainer = c(ColorMath.mix(accent, WHITE, DARK_INK)),
            inversePrimary = c(accent),
            secondary = c(ColorMath.mix(accent, WHITE, HALF)),
            onSecondary = c(DARK_ON_PRIMARY),
            secondaryContainer = c(ColorMath.mix(accent, DARK_GROUND, DARK_DIM)),
            onSecondaryContainer = c(ColorMath.mix(accent, WHITE, LIGHT_TEXT_MIX)),
            tertiary = c(ColorMath.mix(accent, WHITE, DARK_TERTIARY)),
            onTertiary = c(DARK_ON_PRIMARY),
            tertiaryContainer = c(ColorMath.mix(accent, DARK_GROUND, DARK_DIMMER)),
            onTertiaryContainer = c(ColorMath.mix(accent, WHITE, LIGHT_TEXT_MIX)),
            background = c(DARK_GROUND),
            onBackground = c(DARK_TEXT),
            surface = c(DARK_GROUND),
            onSurface = c(DARK_TEXT),
            surfaceVariant = c(0x2C231B),
            onSurfaceVariant = c(0xC4B4A2),
            surfaceTint = c(primary),
            inverseSurface = c(DARK_TEXT),
            inverseOnSurface = c(0x2C231B),
            outline = c(0x8F7F6E),
            outlineVariant = c(0x3E342A),
            surfaceBright = c(0x3A3028),
            surfaceDim = c(DARK_GROUND),
            surfaceContainerLowest = c(0x110D0A),
            surfaceContainerLow = c(0x1C1611),
            surfaceContainer = c(0x211A14),
            surfaceContainerHigh = c(0x2C231B),
            surfaceContainerHighest = c(0x362C23)
        )
    }

    private fun c(rgb: Int) = Color(OPAQUE or rgb)

    private const val OPAQUE = 0xFF shl 24
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000
    private const val LIGHT_GROUND = 0xFFF8F1
    private const val LIGHT_INK = 0x22190F
    private const val DARK_GROUND = 0x16110D
    private const val DARK_TEXT = 0xF5EBDF
    private const val DARK_ON_PRIMARY = 0x1A0F07
    private const val SOFT = 0.8f
    private const val INK = 0.45f
    private const val DARK_LIFT = 0.3f
    private const val DARK_SOFT = 0.62f
    private const val DARK_INK = 0.62f
    private const val DEEP = 0.3f
    private const val SLIGHT = 0.15f
    private const val PALE = 0.86f
    private const val PALER = 0.9f
    private const val DARK_TEXT_MIX = 0.55f
    private const val HALF = 0.5f
    private const val DARK_TERTIARY = 0.4f
    private const val DARK_DIM = 0.72f
    private const val DARK_DIMMER = 0.78f
    private const val LIGHT_TEXT_MIX = 0.7f
}
