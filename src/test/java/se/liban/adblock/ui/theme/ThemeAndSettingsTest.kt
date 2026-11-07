package se.liban.adblock.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.liban.adblock.data.AppSettings
import se.liban.adblock.data.UpdateFrequency
import se.liban.adblock.data.toAppSettings
import se.liban.adblock.data.toSnapshot

class ThemeAndSettingsTest {
    @Test
    fun lightModeIgnoresSystemDark() {
        assertEquals(false, ThemeMode.LIGHT.resolveDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.LIGHT.resolveDark(systemInDarkTheme = false))
    }

    @Test
    fun darkModeIgnoresSystemLight() {
        assertEquals(true, ThemeMode.DARK.resolveDark(systemInDarkTheme = true))
        assertEquals(true, ThemeMode.DARK.resolveDark(systemInDarkTheme = false))
    }

    @Test
    fun systemModeFollowsTheDevice() {
        assertEquals(true, ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = false))
    }

    @Test
    fun unknownThemeNameFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("sepia"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
    }

    @Test
    fun textStaysReadableInBothPalettes() {
        AdBlockPalette.textPairs.forEach { (foreground, background) ->
            assertTrue(
                "Contrast ${contrastRatio(foreground, background)} for ${foreground.toHex()} on ${background.toHex()}",
                contrastRatio(foreground, background) >= 4.5
            )
        }
    }

    @Test
    fun bordersStayVisibleInBothPalettes() {
        AdBlockPalette.borderPairs.forEach { (foreground, background) ->
            assertTrue(
                "Border contrast ${contrastRatio(foreground, background)} for ${foreground.toHex()} on ${background.toHex()}",
                contrastRatio(foreground, background) >= 3.0
            )
        }
    }

    @Test
    fun settingsRoundTripKeepsEveryAppearanceMode() {
        ThemeMode.entries.forEach { mode ->
            val original = AppSettings(
                themeMode = mode,
                automaticUpdates = true,
                updateFrequency = UpdateFrequency.EVERY_12_HOURS,
                wifiOnly = true,
                backgroundUpdates = true,
                vpnUnmetered = false,
                restartVpnAfterUpdate = true,
                hostsRebootReminder = false,
                resumeVpnOnLaunch = true
            )
            assertEquals(original, original.toSnapshot().toAppSettings())
        }
    }

    @Test
    fun damagedPreferenceValuesUseSafeDefaults() {
        val restored = se.liban.adblock.data.SettingsSnapshot(
            themeMode = "nope",
            updateFrequency = "hourly"
        ).toAppSettings()
        assertEquals(ThemeMode.SYSTEM, restored.themeMode)
        assertEquals(UpdateFrequency.DAILY, restored.updateFrequency)
        assertEquals(se.liban.adblock.data.BlocklistCatalog.DEFAULT_ID, restored.blocklistId)
        assertEquals(false, restored.automaticUpdates)
        assertEquals(true, restored.vpnUnmetered)
        assertEquals(true, restored.hostsRebootReminder)
    }

    @Test
    fun updateFrequencyMatchesItsLabel() {
        assertEquals(6L * 60L * 60L * 1000L, UpdateFrequency.EVERY_6_HOURS.intervalMillis)
        assertEquals(24L * 60L * 60L * 1000L, UpdateFrequency.DAILY.intervalMillis)
        assertEquals(168L * 60L * 60L * 1000L, UpdateFrequency.WEEKLY.intervalMillis)
    }

    private fun Long.toHex(): String = "0x${toString(16)}"
}
