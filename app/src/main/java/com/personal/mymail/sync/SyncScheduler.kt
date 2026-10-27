package com.personal.mymail.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.mymail.data.AppSettings
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "mail-sync"

    /** Starts, updates or cancels the background check to match the saved settings. */
    fun apply(context: Context) {
        val settings = AppSettings(context)
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.backgroundSync) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            settings.intervalMinutes.toLong().coerceAtLeast(15L),
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
