package com.smsexpense.tracker.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.service.bubble.BubbleLauncher
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manifest-registered so it fires even when the app process is dead.
 *
 * Handles: single SMS, multipart SMS (parts merged in order), several distinct
 * messages inside one intent (grouped by originating address).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val merged = try {
            mergeMessages(intent)
        } catch (e: Exception) {
            AppLog.e("Failed to read incoming SMS", e)
            return
        }
        if (merged.isEmpty()) return

        val container = context.appContainer()
        val pending = goAsync()
        container.applicationScope.launch(Dispatchers.IO) {
            try {
                for (message in merged) {
                    when (val outcome = container.ingestPaymentMessage(message)) {
                        is IngestOutcome.PaymentSaved -> {
                            AppLog.d("Payment saved id=${outcome.paymentId} confidence=${outcome.confidence}")
                            BubbleLauncher.launchIfPossible(context, outcome.paymentId)
                        }
                        IngestOutcome.DuplicateIgnored -> AppLog.d("Duplicate SMS ignored")
                        is IngestOutcome.NotFromBank -> AppLog.d("Ignored sender (not a configured bank)")
                        is IngestOutcome.NotAPayment -> AppLog.d("Not a payment: ${outcome.reason}")
                        is IngestOutcome.BelowThreshold ->
                            AppLog.d("Below threshold ${outcome.confidence} < ${outcome.threshold}")
                    }
                }
            } catch (e: Exception) {
                AppLog.e("SMS ingestion failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Merges the intent's PDUs into complete logical messages. Multipart SMS arrive
         * as several SmsMessage parts sharing the same originating address — their bodies
         * are concatenated in array order (the order the framework provides them).
         */
        fun mergeMessages(intent: Intent): List<IncomingMessage> {
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()
            if (parts.isEmpty()) return emptyList()

            data class Acc(val bodies: StringBuilder, val timestamp: Long)

            val byAddress = LinkedHashMap<String, Acc>()
            for (part in parts) {
                if (part == null) continue
                val address = part.originatingAddress ?: continue
                val body = try {
                    part.messageBody ?: ""
                } catch (e: Exception) {
                    "" // Corrupt/unreadable PDU: skip its body, keep the rest.
                }
                val acc = byAddress.getOrPut(address) { Acc(StringBuilder(), part.timestampMillis) }
                acc.bodies.append(body)
            }
            return byAddress.map { (address, acc) ->
                IncomingMessage(
                    sender = address,
                    body = acc.bodies.toString(),
                    timestamp = acc.timestamp,
                )
            }.filter { it.body.isNotBlank() }
        }
    }
}
