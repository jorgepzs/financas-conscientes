package br.com.financasconscientes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAppearanceTest {
    @Test
    fun explicitDarkModeOverridesLightSystem() {
        assertTrue(AppAppearance.shouldUseNight(AppAppearance.MODE_DARK, systemNight = false))
    }

    @Test
    fun explicitLightModeOverridesDarkSystem() {
        assertFalse(AppAppearance.shouldUseNight(AppAppearance.MODE_LIGHT, systemNight = true))
    }

    @Test
    fun systemModeFollowsDeviceAppearance() {
        assertTrue(AppAppearance.shouldUseNight(AppAppearance.MODE_SYSTEM, systemNight = true))
        assertFalse(AppAppearance.shouldUseNight(AppAppearance.MODE_SYSTEM, systemNight = false))
    }

    @Test
    fun unknownModeSafelyFallsBackToSystem() {
        assertTrue(AppAppearance.shouldUseNight("unexpected", systemNight = true))
        assertFalse(AppAppearance.shouldUseNight(null, systemNight = false))
    }
}
