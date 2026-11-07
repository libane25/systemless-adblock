package se.liban.adblock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun set(settings: AppSettings) {
        val normalized = settings.copy(
            blocklistId = BlocklistCatalog.require(settings.blocklistId).id
        )
        val previous = _settings.value
        val snapshot = normalized.toSnapshot()
        prefs.edit()
            .putString(KEY_THEME, snapshot.themeMode)
            .putBoolean(KEY_AUTOMATIC, snapshot.automaticUpdates)
            .putString(KEY_FREQUENCY, snapshot.updateFrequency)
            .putBoolean(KEY_WIFI, snapshot.wifiOnly)
            .putBoolean(KEY_BACKGROUND, snapshot.backgroundUpdates)
            .putBoolean(KEY_VPN_UNMETERED, snapshot.vpnUnmetered)
            .putBoolean(KEY_RESTART_VPN, snapshot.restartVpnAfterUpdate)
            .putBoolean(KEY_HOSTS_REMINDER, snapshot.hostsRebootReminder)
            .putBoolean(KEY_RESUME_VPN, snapshot.resumeVpnOnLaunch)
            .putString(KEY_BLOCKLIST, snapshot.blocklistId)
            .commit()
        _settings.value = normalized
        if (previous.schedulesUpdates != settings.schedulesUpdates) {
            UpdateScheduler.sync(appContext, settings)
        }
    }

    fun lastAutoAttempt(): Long = prefs.getLong(KEY_AUTO_ATTEMPT, 0L)

    fun markAutoAttempt(timeMillis: Long) {
        prefs.edit().putLong(KEY_AUTO_ATTEMPT, timeMillis).apply()
    }

    private fun read(): AppSettings = SettingsSnapshot(
        themeMode = prefs.getString(KEY_THEME, null),
        automaticUpdates = prefs.getBoolean(KEY_AUTOMATIC, false),
        updateFrequency = prefs.getString(KEY_FREQUENCY, null),
        wifiOnly = prefs.getBoolean(KEY_WIFI, false),
        backgroundUpdates = prefs.getBoolean(KEY_BACKGROUND, false),
        vpnUnmetered = prefs.getBoolean(KEY_VPN_UNMETERED, true),
        restartVpnAfterUpdate = prefs.getBoolean(KEY_RESTART_VPN, false),
        hostsRebootReminder = prefs.getBoolean(KEY_HOSTS_REMINDER, true),
        resumeVpnOnLaunch = prefs.getBoolean(KEY_RESUME_VPN, false),
        blocklistId = prefs.getString(KEY_BLOCKLIST, null)
    ).toAppSettings()

    private val AppSettings.schedulesUpdates: String
        get() = "$automaticUpdates|$backgroundUpdates|${updateFrequency.name}|$wifiOnly"

    private companion object {
        const val PREFS = "adblock_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTOMATIC = "automatic_updates"
        const val KEY_FREQUENCY = "update_frequency"
        const val KEY_WIFI = "wifi_only"
        const val KEY_BACKGROUND = "background_updates"
        const val KEY_VPN_UNMETERED = "vpn_unmetered"
        const val KEY_RESTART_VPN = "restart_vpn_after_update"
        const val KEY_HOSTS_REMINDER = "hosts_reboot_reminder"
        const val KEY_RESUME_VPN = "resume_vpn_on_launch"
        const val KEY_BLOCKLIST = "blocklist_id"
        const val KEY_AUTO_ATTEMPT = "auto_attempt_at"
    }
}
