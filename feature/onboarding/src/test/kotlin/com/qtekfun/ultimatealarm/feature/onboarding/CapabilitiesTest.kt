package com.qtekfun.ultimatealarm.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilitiesTest {
    private val allGood = Capabilities(
        exactAlarms = true,
        fullScreenIntent = true,
        notifications = true,
        ignoringBatteryOptimizations = true,
        nfc = true,
        camera = true,
        powerSaveMode = false,
        manufacturer = "Google",
        model = "Pixel 8",
        androidVersion = "16",
        sdk = 36
    )

    @Test
    fun `nothing critical is missing when everything is granted`() {
        assertEquals(emptyList<Capability>(), allGood.missingCritical())
    }

    @Test
    fun `battery optimisation, NFC and camera are not critical`() {
        val device = allGood.copy(ignoringBatteryOptimizations = false, nfc = false, camera = false, powerSaveMode = true)
        assertEquals(emptyList<Capability>(), device.missingCritical())
    }

    @Test
    fun `missing critical capabilities are listed in order`() {
        val device = allGood.copy(exactAlarms = false, notifications = false, fullScreenIntent = false)
        assertEquals(listOf(Capability.EXACT_ALARMS, Capability.NOTIFICATIONS, Capability.FULL_SCREEN_INTENT), device.missingCritical())
    }
}
