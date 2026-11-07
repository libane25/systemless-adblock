package se.liban.adblock.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val WORK_NAME = "adblock_blocklist_update"

    fun sync(context: Context, settings: AppSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.automaticUpdates || !settings.backgroundUpdates) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(
            settings.updateFrequency.hours.toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
