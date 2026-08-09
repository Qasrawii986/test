package com.smsexpense.tracker.service.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsexpense.tracker.appContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val UNIQUE_WORK = "payment-sync"

    /** No-op while the API is disabled in settings, so offline-first stays truly offline. */
    suspend fun scheduleIfEnabled(context: Context) {
        val api = context.appContainer().settingsRepository.apiSettings.first()
        if (!api.enabled) return
        schedule(context)
    }

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
