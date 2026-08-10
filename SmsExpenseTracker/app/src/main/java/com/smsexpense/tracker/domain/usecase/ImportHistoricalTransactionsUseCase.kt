package com.smsexpense.tracker.domain.usecase

import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.parser.DedupKey
import com.smsexpense.tracker.domain.parser.ParseOutcome
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.repository.ImportHistoryRepository
import com.smsexpense.tracker.domain.repository.IngestResult
import com.smsexpense.tracker.domain.repository.PaymentRepository
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.domain.source.DeviceSmsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/** A parsed historical payment waiting for user review. */
data class HistoricalCandidate(
    val candidate: PaymentCandidate,
)

sealed class ScanEvent {
    data class Progress(val messagesScanned: Int, val transactionsFound: Int) : ScanEvent()
    data class Done(
        val candidates: List<HistoricalCandidate>,
        val messagesScanned: Int,
        val alreadyImported: Int,
    ) : ScanEvent()
}

data class ImportSummary(
    val imported: Int,
    val duplicatesSkipped: Int,
    val total: Double,
    val currency: String,
)

/**
 * Historical import pipeline. Deliberately reuses the exact same [SmsParser],
 * confidence threshold, default-currency fallback and [DedupKey] the realtime
 * receiver uses, so a message produces identical results through either path.
 * Knows nothing about UI.
 */
class ImportHistoricalTransactionsUseCase(
    private val smsSource: DeviceSmsSource,
    private val parser: SmsParser,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val importHistoryRepository: ImportHistoryRepository,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Reads inbox messages in [from, to], keeps those from [senders], parses them,
     * and drops anything already in the database (exact dedup key OR a same
     * sender+body+amount row within [SIMILAR_WINDOW_MS] — covers the SMSC-vs-inbox
     * timestamp difference for messages the realtime receiver already captured).
     */
    fun scan(from: Long, to: Long, senders: Set<String>): Flow<ScanEvent> = flow {
        val threshold = settingsRepository.confidenceThreshold.first()
        val defaultCurrency = settingsRepository.defaultCurrency.first()
        val normalizedSenders = senders.map { it.trim() }.filter { it.isNotEmpty() }

        val messages = smsSource.messagesBetween(from, to)
        val found = mutableListOf<PaymentCandidate>()
        var scanned = 0

        for (message in messages) {
            scanned++
            if (message.timestamp < from || message.timestamp > to) continue
            if (normalizedSenders.none { it.equals(message.sender.trim(), ignoreCase = true) }) {
                if (scanned % PROGRESS_EVERY == 0) emit(ScanEvent.Progress(scanned, found.size))
                continue
            }
            val outcome = try {
                parser.parse(message)
            } catch (_: Exception) {
                null // one unparseable SMS must never kill the scan
            }
            if (outcome is ParseOutcome.Payment && outcome.candidate.confidence >= threshold) {
                val candidate = outcome.candidate
                found.add(
                    if (candidate.currency.isBlank()) candidate.copy(currency = defaultCurrency)
                    else candidate
                )
            }
            if (scanned % PROGRESS_EVERY == 0) emit(ScanEvent.Progress(scanned, found.size))
        }
        emit(ScanEvent.Progress(scanned, found.size))

        // Dedup pass 1: exact keys already in the DB (previous imports of the same range).
        val keys = found.associateWith { DedupKey.of(it) }
        val existing = paymentRepository.existingDedupKeys(keys.values.toList())

        // Dedup pass 2: realtime-captured twins with a slightly different timestamp.
        val fresh = mutableListOf<PaymentCandidate>()
        var alreadyImported = 0
        for (candidate in found) {
            val isDuplicate = keys.getValue(candidate) in existing ||
                paymentRepository.hasSimilar(
                    sender = candidate.sender.trim(),
                    message = candidate.originalMessage,
                    amount = candidate.amount,
                    timestamp = candidate.timestamp,
                    windowMs = SIMILAR_WINDOW_MS,
                )
            if (isDuplicate) alreadyImported++ else fresh.add(candidate)
        }

        emit(
            ScanEvent.Done(
                candidates = fresh
                    .sortedByDescending { it.timestamp }
                    .map { HistoricalCandidate(it) },
                messagesScanned = scanned,
                alreadyImported = alreadyImported,
            )
        )
    }.flowOn(dispatcher)

    /**
     * Inserts the reviewed selection. Idempotent: rows are inserted with
     * OnConflict=IGNORE on the dedup key, so re-running can only skip, never double.
     * Imported payments enter the normal sync queue (syncStatus PENDING).
     */
    suspend fun import(
        selection: List<Pair<PaymentCandidate, Long?>>,
        fromDate: Long,
        toDate: Long,
    ): ImportSummary {
        var imported = 0
        var duplicates = 0
        var total = 0.0
        var currency = ""
        for ((candidate, categoryId) in selection) {
            when (paymentRepository.ingest(candidate, PaymentSource.SMS_HISTORICAL, categoryId)) {
                is IngestResult.Inserted -> {
                    imported++
                    total += candidate.amount
                    if (currency.isEmpty()) currency = candidate.currency
                }
                IngestResult.Duplicate -> duplicates++
            }
        }
        if (imported > 0) {
            importHistoryRepository.record(fromDate, toDate, imported, total, currency)
        }
        return ImportSummary(imported, duplicates, total, currency)
    }

    companion object {
        const val PROGRESS_EVERY = 50
        /** ±12h covers SMSC-timestamp vs device-received-timestamp drift. */
        const val SIMILAR_WINDOW_MS = 12 * 60 * 60 * 1000L
    }
}
