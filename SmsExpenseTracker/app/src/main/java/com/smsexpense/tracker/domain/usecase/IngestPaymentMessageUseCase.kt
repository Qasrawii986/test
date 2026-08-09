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
                when (val result = paymentRepository.ingest(withCurrency)) {
                    is IngestResult.Inserted ->
                        IngestOutcome.PaymentSaved(result.paymentId, withCurrency.confidence)
                    IngestResult.Duplicate -> IngestOutcome.DuplicateIgnored
                }
            }
        }
    }
}
