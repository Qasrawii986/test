package com.smsexpense.tracker.service.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.service.sync.SyncScheduler
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles a category button tapped straight from the notification: categorizes
 * the payment through the same repository the bubble uses, then dismisses the
 * notification. Keeps the one-tap promise alive without the overlay.
 */
class CategorizeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CATEGORIZE) return
        val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, -1L)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        if (paymentId <= 0 || categoryId <= 0) return

        val container = context.appContainer()
        val pending = goAsync()
        container.applicationScope.launch(Dispatchers.IO) {
            try {
                container.paymentRepository.categorize(paymentId, categoryId)
                PaymentNotifier.cancel(context, paymentId)
                SyncScheduler.scheduleIfEnabled(context)
            } catch (e: Exception) {
                AppLog.e("Categorizing from notification failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CATEGORIZE = "com.smsexpense.tracker.CATEGORIZE"
        const val EXTRA_PAYMENT_ID = "payment_id"
        const val EXTRA_CATEGORY_ID = "category_id"

        fun pendingIntent(context: Context, paymentId: Long, categoryId: Long): PendingIntent {
            val intent = Intent(context, CategorizeActionReceiver::class.java)
                .setAction(ACTION_CATEGORIZE)
                .putExtra(EXTRA_PAYMENT_ID, paymentId)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
            // Unique request code per payment+category so actions never collide.
            val requestCode = (paymentId * 31 + categoryId).toInt()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
