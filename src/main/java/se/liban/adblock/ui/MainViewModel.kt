package se.liban.adblock.ui

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import se.liban.adblock.data.AppSettings
import se.liban.adblock.data.BlockStatePrefs
import se.liban.adblock.data.BlocklistCatalog
import se.liban.adblock.data.HostsManager
import se.liban.adblock.data.RootExecutor
import se.liban.adblock.data.SettingsRepository
import se.liban.adblock.data.UserMessageException
import se.liban.adblock.data.displayLabel
import se.liban.adblock.ui.theme.ThemeMode
import se.liban.adblock.ui.theme.nightMode
import se.liban.adblock.vpn.AdBlockVpnService
import se.liban.adblock.vpn.VpnStatus
import java.util.concurrent.TimeUnit

enum class BlockMode { HOSTS, VPN }

enum class AppDestination { HOME, SETTINGS, LICENSES }

data class MainUiState(
    val rootGranted: Boolean = false,
    val protected: Boolean = false,
    val blockedDomains: Int = 0,
    val lastUpdatedEpochMs: Long? = null,
    val targetLabel: String = "Not applied yet",
    val working: Boolean = false,
    val stage: String = "",
    val progress: Float? = null,
    val mode: BlockMode = BlockMode.HOSTS,
    val blocklistLabel: String = "StevenBlack Unified"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(BlockStatePrefs.NAME, Context.MODE_PRIVATE)
    private val settingsRepository = SettingsRepository(application)
    private val rootExecutor = RootExecutor(application)
    private val hostsManager = HostsManager(
        application,
        rootExecutor,
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.MINUTES)
            .build()
    )
    private val operation = Mutex()
    private var resumeHandled = false

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val _destination = MutableStateFlow(AppDestination.HOME)
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _vpnRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val vpnRequests: SharedFlow<Unit> = _vpnRequests.asSharedFlow()

    init {
        refreshRootStatus()
        viewModelScope.launch {
            settingsRepository.settings.collect { selected ->
                val label = BlocklistCatalog.require(selected.blocklistId).label
                _state.update { it.copy(blocklistLabel = label) }
            }
        }
        viewModelScope.launch {
            VpnStatus.running.collect { running ->
                _state.update { state ->
                    if (state.mode == BlockMode.VPN) state.copy(protected = running) else state
                }
            }
        }
    }

    fun onAppResumed() {
        if (!_state.value.working) reloadPersisted()
        refreshRootStatus()
        val settings = settingsRepository.current()
        val updating = maybeAutoUpdate(settings)
        if (!resumeHandled) {
            resumeHandled = true
            if (!updating &&
                settings.resumeVpnOnLaunch &&
                _state.value.mode == BlockMode.VPN &&
                _state.value.protected &&
                !VpnStatus.running.value
            ) {
                _vpnRequests.tryEmit(Unit)
            }
        }
    }

    fun openSettings() {
        _destination.value = AppDestination.SETTINGS
    }

    fun openLicenses() {
        _destination.value = AppDestination.LICENSES
    }

    fun goBack() {
        _destination.value = when (_destination.value) {
            AppDestination.LICENSES -> AppDestination.SETTINGS
            else -> AppDestination.HOME
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val current = settingsRepository.current()
        if (current.themeMode == mode) return
        settingsRepository.set(current.copy(themeMode = mode))
        AppCompatDelegate.setDefaultNightMode(mode.nightMode())
    }

    fun updateSettings(settings: AppSettings) {
        settingsRepository.set(settings)
    }

    fun refreshRootStatus() {
        viewModelScope.launch {
            val granted = rootExecutor.peekGranted()
            _state.update { it.copy(rootGranted = granted) }
        }
    }

    fun setMode(mode: BlockMode) {
        prefs.edit().putString(BlockStatePrefs.MODE, mode.name).apply()
        _state.update { it.copy(mode = mode) }
        if (mode == BlockMode.HOSTS) AdBlockVpnService.stop(getApplication())
    }

    fun enableOrUpdate() {
        val vpn = _state.value.mode == BlockMode.VPN
        runOperation(needsRoot = !vpn) {
            if (vpn) {
                val count = hostsManager.saveVpnBlocklist { fraction ->
                    _state.update {
                        it.copy(working = true, stage = "Downloading block list", progress = fraction)
                    }
                }
                val restartRunning = VpnStatus.running.value &&
                    settingsRepository.current().restartVpnAfterUpdate
                persist(
                    blockedDomains = count,
                    protected = VpnStatus.running.value || restartRunning,
                    targetLabel = "VPN like AdBlock"
                )
                if (restartRunning) {
                    AdBlockVpnService.restart(getApplication())
                    toast("Block list updated. VPN reloaded.")
                } else {
                    _vpnRequests.tryEmit(Unit)
                    toast("Block list ready. Allow the VPN to start AdBlock.")
                }
            } else {
                val result = hostsManager.installLatest { fraction ->
                    _state.update {
                        it.copy(
                            working = true,
                            stage = "Downloading block list",
                            progress = fraction
                        )
                    }
                }
                persist(
                    blockedDomains = result.blockedDomains,
                    protected = true,
                    targetLabel = result.outcome.target.kind.displayLabel()
                )
                val applied = hostsAppliedMessage(result.outcome.mountedNow)
                toast("$applied. ${result.blockedDomains} domains blocked.")
            }
        }
    }

    fun revertToDefault() {
        val vpn = _state.value.mode == BlockMode.VPN
        runOperation(needsRoot = !vpn) {
            if (vpn) {
                AdBlockVpnService.stop(getApplication())
                _state.update { it.copy(protected = false, targetLabel = "VPN like AdBlock") }
                toast("VPN ad blocking stopped")
            } else {
                _state.update { it.copy(stage = "Restoring default hosts", progress = null) }
                val result = hostsManager.revertToDefault()
                persist(
                    blockedDomains = 0,
                    protected = false,
                    targetLabel = result.outcome.target.kind.displayLabel()
                )
                toast(
                    if (result.outcome.mountedNow) {
                        "Restored default localhost hosts"
                    } else {
                        hostsRevertedMessage()
                    }
                )
            }
        }
    }

    private fun maybeAutoUpdate(settings: AppSettings): Boolean {
        if (!settings.automaticUpdates) return false
        val now = System.currentTimeMillis()
        val lastSuccess = prefs.getLong(BlockStatePrefs.UPDATED, 0L)
        val lastAttempt = settingsRepository.lastAutoAttempt()
        if (now - maxOf(lastSuccess, lastAttempt) < settings.updateFrequency.intervalMillis) return false
        if (_state.value.working) return false
        settingsRepository.markAutoAttempt(now)
        enableOrUpdate()
        return true
    }

    private fun hostsAppliedMessage(mountedNow: Boolean): String = when {
        mountedNow -> "Block list applied"
        settingsRepository.current().hostsRebootReminder ->
            "Block list saved. Reboot so the module mount picks it up"
        else -> "Block list saved"
    }

    private fun hostsRevertedMessage(): String = if (settingsRepository.current().hostsRebootReminder) {
        "Default hosts saved. Reboot so the module mount picks it up"
    } else {
        "Default hosts saved"
    }

    private fun reloadPersisted() {
        val fresh = readPersisted()
        _state.update { current ->
            current.copy(
                protected = if (fresh.mode == BlockMode.VPN) VpnStatus.running.value else fresh.protected,
                blockedDomains = fresh.blockedDomains,
                lastUpdatedEpochMs = fresh.lastUpdatedEpochMs,
                targetLabel = fresh.targetLabel,
                mode = fresh.mode
            )
        }
    }

    private fun runOperation(needsRoot: Boolean, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!operation.tryLock()) return@launch
            try {
                _state.update {
                    it.copy(
                        working = true,
                        stage = if (needsRoot) "Requesting root" else "Preparing",
                        progress = null
                    )
                }
                if (needsRoot) {
                    val granted = rootExecutor.requestRoot()
                    _state.update { it.copy(rootGranted = granted) }
                    if (!granted) {
                        toast("Root access denied")
                        return@launch
                    }
                }
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserMessageException) {
                toast(e.message ?: "Something went wrong")
            } catch (e: Exception) {
                toast(e.message?.take(180) ?: "Something went wrong")
            } finally {
                _state.update { it.copy(working = false, stage = "", progress = null) }
                operation.unlock()
            }
        }
    }

    private fun persist(blockedDomains: Int, protected: Boolean, targetLabel: String) {
        val updatedAt = System.currentTimeMillis()
        prefs.edit()
            .putInt(BlockStatePrefs.COUNT, blockedDomains)
            .putLong(BlockStatePrefs.UPDATED, updatedAt)
            .putBoolean(BlockStatePrefs.PROTECTED, protected)
            .putString(BlockStatePrefs.TARGET, targetLabel)
            .apply()
        _state.update {
            it.copy(
                protected = protected,
                blockedDomains = blockedDomains,
                lastUpdatedEpochMs = updatedAt,
                targetLabel = targetLabel
            )
        }
    }

    private fun readPersisted(): MainUiState {
        val updated = prefs.getLong(BlockStatePrefs.UPDATED, 0L)
        return MainUiState(
            protected = prefs.getBoolean(BlockStatePrefs.PROTECTED, false),
            blockedDomains = prefs.getInt(BlockStatePrefs.COUNT, 0),
            lastUpdatedEpochMs = updated.takeIf { it > 0L },
            targetLabel = prefs.getString(BlockStatePrefs.TARGET, null) ?: "Not applied yet",
            mode = prefs.getString(BlockStatePrefs.MODE, BlockMode.HOSTS.name)
                ?.let { runCatching { BlockMode.valueOf(it) }.getOrNull() }
                ?: BlockMode.HOSTS,
            blocklistLabel = BlocklistCatalog.require(settingsRepository.current().blocklistId).label
        )
    }

    private fun toast(message: String) {
        _messages.tryEmit(message)
    }
}
