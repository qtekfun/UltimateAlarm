package com.qtekfun.ultimatealarm.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun rgb(color: Color) = color.toArgb() and 0xFFFFFF

    private fun assertReadable(name: String, fg: Color, bg: Color, minimum: Double = 4.5) {
        val ratio = ColorMath.contrast(rgb(fg), rgb(bg))
        assertTrue("$name: contrast $ratio < $minimum", ratio >= minimum)
    }

    @Test
    fun `mix moves each channel by the amount`() {
        assertEquals(0xFFFFFF, ColorMath.mix(0x000000, 0xFFFFFF, 1f))
        assertEquals(0x000000, ColorMath.mix(0x000000, 0xFFFFFF, 0f))
        assertEquals(0x808080, ColorMath.mix(0x000000, 0xFFFFFF, 0.5f))
        assertEquals(0xC2410C, ColorMath.mix(0xC2410C, 0x123456, 0f))
    }

    @Test
    fun `contrast follows WCAG`() {
        assertEquals(21.0, ColorMath.contrast(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, ColorMath.contrast(0x777777, 0x777777), 0.001)
    }

    @Test
    fun `every accent keeps text readable in light and dark`() {
        AccentColor.entries.filter { it != AccentColor.DYNAMIC }.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val s = AmanecerPalette.scheme(dark, accent.rgb)
                val tag = "$accent dark=$dark"
                assertReadable("$tag onPrimary", s.onPrimary, s.primary)
                assertReadable("$tag onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer)
                assertReadable("$tag onSurface", s.onSurface, s.surface)
                assertReadable("$tag onSurfaceVariant", s.onSurfaceVariant, s.surface)
                assertReadable("$tag onSurfaceVariant on cards", s.onSurfaceVariant, s.surfaceContainer)
                assertReadable("$tag onSecondary", s.onSecondary, s.secondary)
                assertReadable("$tag onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
                assertReadable("$tag onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
                assertReadable("$tag primary as text", s.primary, s.surface, minimum = 3.0)
            }
        }
    }
}
