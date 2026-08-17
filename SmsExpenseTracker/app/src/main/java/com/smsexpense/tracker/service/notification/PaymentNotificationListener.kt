package com.smsexpense.tracker.service.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.service.bubble.BubbleBlocker
import com.smsexpense.tracker.service.bubble.BubbleLauncher
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Second ingestion channel: payment notifications from wallet and bank apps.
 *
 * A contactless tap cannot be observed directly — Android routes the transaction
 * to the wallet app that owns the payment AID and exposes nothing to other apps.
 * The notification the wallet or bank posts immediately afterwards is the closest
 * legitimate signal, and it usually beats the bank's SMS.
 *
 * Everything downstream is shared with SMS: same parser, same confidence
 * threshold, same deduplication, same bubble.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val container = appContainer()
        container.applicationScope.launch(Dispatchers.IO) {
            try {
                val watched = container.settingsRepository.notificationPackages.first()
                if (sbn.packageName !in watched) return@launch
                // Never react to our own notifications — that would loop.
                if (sbn.packageName == packageName) return@launch

                val body = extractText(sbn) ?: return@launch
                val message = IncomingMessage(
                    sender = appLabel(sbn.packageName),
                    body = body,
                    timestamp = sbn.postTime,
                )
                when (
                    val outcome = container.ingestPaymentMessage(
                        message,
                        skipSenderFilter = true, // the package allowlist is the filter here
                        source = PaymentSource.NOTIFICATION,
                    )
                ) {
                    is IngestOutcome.PaymentSaved -> {
                        AppLog.d("Payment from notification id=${outcome.paymentId}")
                        val blocker = BubbleLauncher.launchIfPossible(this@PaymentNotificationListener, outcome.paymentId)
                        if (blocker != BubbleBlocker.NONE) {
                            val payment = container.paymentRepository.getById(outcome.paymentId)
                            if (payment != null) {
                                PaymentNotifier.notifyUncategorized(
                                    this@PaymentNotificationListener,
                                    payment,
                                    container.categoryRepository.getAll(),
                                    blocker,
                                )
                            }
                        }
                    }
                    IngestOutcome.DuplicateIgnored -> AppLog.d("Duplicate notification ignored")
                    else -> AppLog.d("Notification was not a payment")
                }
            } catch (e: Exception) {
                AppLog.e("Notification ingestion failed", e)
            }
        }
    }

    /** Title + text + big text, so amounts in any of them are visible to the parser. */
    private fun extractText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val parts = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        ).distinct()
        val body = parts.joinToString(" ").trim()
        return body.ifBlank { null }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    companion object {
        /** Whether the user has granted notification access to this app. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
