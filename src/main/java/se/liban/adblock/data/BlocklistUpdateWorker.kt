package se.liban.adblock.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import se.liban.adblock.ui.BlockMode
import java.util.concurrent.TimeUnit

class BlocklistUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext).current()
        if (!settings.automaticUpdates || !settings.backgroundUpdates) return Result.success()
        if (settings.wifiOnly && !applicationContext.isWifiOrEthernet()) return Result.success()
        return try {
            val root = RootExecutor(applicationContext)
            val hosts = HostsManager(applicationContext, root, httpClient())
            val prefs = applicationContext.getSharedPreferences(BlockStatePrefs.NAME, Context.MODE_PRIVATE)
            val mode = prefs.getString(BlockStatePrefs.MODE, BlockMode.HOSTS.name)
            if (mode == BlockMode.VPN.name) {
                val count = hosts.saveVpnBlocklist { }
                prefs.edit()
                    .putInt(BlockStatePrefs.COUNT, count)
                    .putLong(BlockStatePrefs.UPDATED, System.currentTimeMillis())
                    .putString(BlockStatePrefs.TARGET, "VPN like AdBlock")
                    .apply()
            } else if (root.peekGranted()) {
                val result = hosts.installLatest { }
                prefs.edit()
                    .putInt(BlockStatePrefs.COUNT, result.blockedDomains)
                    .putLong(BlockStatePrefs.UPDATED, System.currentTimeMillis())
                    .putBoolean(BlockStatePrefs.PROTECTED, true)
                    .putString(BlockStatePrefs.TARGET, result.outcome.target.kind.displayLabel())
                    .apply()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: UserMessageException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.MINUTES)
        .build()
}
