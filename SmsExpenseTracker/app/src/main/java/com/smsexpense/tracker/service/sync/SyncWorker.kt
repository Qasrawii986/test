package com.smsexpense.tracker.service.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.util.AppLog

/**
 * Pushes pending payments to the backend when a network is available. Enqueued by
 * [SyncScheduler] after each categorization and retried with backoff on failure.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        return try {
            val summary = container.syncPayments()
            AppLog.d("Sync finished: synced=${summary.synced} failed=${summary.failed} disabled=${summary.disabled}")
            when {
                summary.disabled -> Result.success()
                summary.failed > 0 && runAttemptCount < MAX_RETRIES -> Result.retry()
                else -> Result.success()
            }
        } catch (e: Exception) {
            AppLog.e("Sync worker crashed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val MAX_RETRIES = 5
    }
}
