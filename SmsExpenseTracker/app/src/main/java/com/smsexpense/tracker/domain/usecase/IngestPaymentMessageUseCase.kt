package com.smsexpense.tracker.domain.usecase

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.parser.ParseOutcome
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.repository.IngestResult
import com.smsexpense.tracker.domain.repository.PaymentRepository
import com.smsexpense.tracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/** Everything the pipeline can say about one incoming message. */
sealed class IngestOutcome {
    data class PaymentSaved(val paymentId: Long, val confidence: Float) : IngestOutcome()
    data object DuplicateIgnored : IngestOutcome()
    data class NotFromBank(val sender: String) : IngestOutcome()
    data class NotAPayment(val reason: String) : IngestOutcome()
    data class BelowThreshold(val confidence: Float, val threshold: Float) : IngestOutcome()
}

/**
 * The single entry point for every message source (SMS receiver, debug simulator,
 * future NotificationListener): filter by sender → parse → threshold → dedupe → save.
 */
class IngestPaymentMessageUseCase(
    private val parser: SmsParser,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        message: IncomingMessage,
        skipSenderFilter: Boolean = false,
        source: com.smsexpense.tracker.domain.model.PaymentSource =
            com.smsexpense.tracker.domain.model.PaymentSource.SMS_REALTIME,
    ): IngestOutcome {
        if (!skipSenderFilter) {
            val allowed = settingsRepository.senderIds.first()
            val senderMatches = allowed.any { it.equals(message.sender.trim(), ignoreCase = true) }
            if (!senderMatches) return IngestOutcome.NotFromBank(message.sender)
        }

        val outcome = try {
            parser.parse(message)
        } catch (e: Exception) {
            return IngestOutcome.NotAPayment("Parser error: ${e.message ?: e.javaClass.simpleName}")
        }

        return when (outcome) {
            is ParseOutcome.NotPayment -> IngestOutcome.NotAPayment(outcome.reason)
            is ParseOutcome.Payment -> {
                val threshold = settingsRepository.confidenceThreshold.first()
                val candidate = outcome.candidate
                if (candidate.confidence < threshold) {
                    return IngestOutcome.BelowThreshold(candidate.confidence, threshold)
                }
                val withCurrency = if (candidate.currency.isBlank()) {
                    candidate.copy(currency = settingsRepository.defaultCurrency.first())
                } else candidate

                // A notification can be re-posted with a fresh timestamp, which would
                // otherwise slip past the exact dedup key.
                if (source == com.smsexpense.tracker.domain.model.PaymentSource.NOTIFICATION &&
                    paymentRepository.hasSimilar(
                        withCurrency.sender, withCurrency.originalMessage,
                        withCurrency.amount, withCurrency.timestamp, REPOST_WINDOW_MS,
                    )
                ) {
                    return IngestOutcome.DuplicateIgnored
                }

                // The same purchase arriving through another channel (wallet
                // notification + bank SMS) must not be counted twice.
                if (paymentRepository.hasCrossSourceTwin(
                        withCurrency.amount, withCurrency.timestamp, CROSS_SOURCE_WINDOW_MS, source,
                    )
                ) {
                    return IngestOutcome.DuplicateIgnored
                }

                when (val result = paymentRepository.ingest(withCurrency, source)) {
                    is IngestResult.Inserted ->
                        IngestOutcome.PaymentSaved(result.paymentId, withCurrency.confidence)
                    IngestResult.Duplicate -> IngestOutcome.DuplicateIgnored
                }
            }
        }
    }

    companion object {
        /** A notification updated in place still describes the same payment. */
        const val REPOST_WINDOW_MS = 5 * 60 * 1000L
        /** Wallet notification and bank SMS for one purchase arrive minutes apart. */
        const val CROSS_SOURCE_WINDOW_MS = 10 * 60 * 1000L
    }
}
