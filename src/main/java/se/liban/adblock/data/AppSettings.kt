package se.liban.adblock.data

import se.liban.adblock.ui.theme.ThemeMode

enum class UpdateFrequency(val hours: Int, val label: String) {
    EVERY_6_HOURS(6, "Every 6 hours"),
    EVERY_12_HOURS(12, "Every 12 hours"),
    DAILY(24, "Daily"),
    WEEKLY(168, "Weekly");

    val intervalMillis: Long get() = hours * 60L * 60L * 1000L

    companion object {
        fun fromName(raw: String?): UpdateFrequency =
            entries.firstOrNull { it.name == raw } ?: DAILY
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val automaticUpdates: Boolean = false,
    val updateFrequency: UpdateFrequency = UpdateFrequency.DAILY,
    val wifiOnly: Boolean = false,
    val backgroundUpdates: Boolean = false,
    val vpnUnmetered: Boolean = true,
    val restartVpnAfterUpdate: Boolean = false,
    val hostsRebootReminder: Boolean = true,
    val resumeVpnOnLaunch: Boolean = false,
    val blocklistId: String = BlocklistCatalog.DEFAULT_ID
)

internal data class SettingsSnapshot(
    val themeMode: String? = null,
    val automaticUpdates: Boolean = false,
    val updateFrequency: String? = null,
    val wifiOnly: Boolean = false,
    val backgroundUpdates: Boolean = false,
    val vpnUnmetered: Boolean = true,
    val restartVpnAfterUpdate: Boolean = false,
    val hostsRebootReminder: Boolean = true,
    val resumeVpnOnLaunch: Boolean = false,
    val blocklistId: String? = null
)

internal fun AppSettings.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
    themeMode = themeMode.name,
    automaticUpdates = automaticUpdates,
    updateFrequency = updateFrequency.name,
    wifiOnly = wifiOnly,
    backgroundUpdates = backgroundUpdates,
    vpnUnmetered = vpnUnmetered,
    restartVpnAfterUpdate = restartVpnAfterUpdate,
    hostsRebootReminder = hostsRebootReminder,
    resumeVpnOnLaunch = resumeVpnOnLaunch,
    blocklistId = blocklistId
)

internal fun SettingsSnapshot.toAppSettings(): AppSettings = AppSettings(
    themeMode = ThemeMode.fromName(themeMode),
    automaticUpdates = automaticUpdates,
    updateFrequency = UpdateFrequency.fromName(updateFrequency),
    wifiOnly = wifiOnly,
    backgroundUpdates = backgroundUpdates,
    vpnUnmetered = vpnUnmetered,
    restartVpnAfterUpdate = restartVpnAfterUpdate,
    hostsRebootReminder = hostsRebootReminder,
    resumeVpnOnLaunch = resumeVpnOnLaunch,
    blocklistId = BlocklistCatalog.require(blocklistId).id
)
