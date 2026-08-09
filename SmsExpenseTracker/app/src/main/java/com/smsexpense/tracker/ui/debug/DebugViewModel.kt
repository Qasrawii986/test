package com.smsexpense.tracker.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.AppContainer
import com.smsexpense.tracker.data.remote.api.ApiResult
import com.smsexpense.tracker.data.remote.dto.PaymentDto
import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.parser.ParseOutcome
import com.smsexpense.tracker.domain.repository.IngestResult
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.service.bubble.BubbleLauncher
import com.smsexpense.tracker.service.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Developer tools. Everything here goes through the REAL pipeline — simulate SMS
 * calls the same ingest use case the broadcast receiver uses.
 */
class DebugViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private fun log(line: String) {
        _log.value = (listOf(line) + _log.value).take(60)
    }

    fun simulateSms(sender: String, body: String, respectSenderFilter: Boolean) {
        viewModelScope.launch {
            val message = IncomingMessage(
                sender = sender.trim(),
                body = body,
                timestamp = System.currentTimeMillis(),
            )
            val outcome = container.ingestPaymentMessage(message, skipSenderFilter = !respectSenderFilter)
            when (outcome) {
                is IngestOutcome.PaymentSaved -> {
                    log("✅ Saved payment #${outcome.paymentId} (confidence ${"%.2f".format(outcome.confidence)})")
                    BubbleLauncher.launchIfPossible(getApplication(), outcome.paymentId)
                }
                IngestOutcome.DuplicateIgnored -> log("♻️ Duplicate — ignored")
                is IngestOutcome.NotFromBank -> log("🚫 Sender '${outcome.sender}' not in configured Sender IDs")
                is IngestOutcome.NotAPayment -> log("ℹ️ Not a payment: ${outcome.reason}")
                is IngestOutcome.BelowThreshold ->
                    log("⬇️ Below threshold: ${"%.2f".format(outcome.confidence)} < ${"%.2f".format(outcome.threshold)}")
            }
        }
    }

    fun testParser(sender: String, body: String) {
        val outcome = container.parser.parse(
            IncomingMessage(sender.trim(), body, System.currentTimeMillis())
        )
        when (outcome) {
            is ParseOutcome.Payment -> {
                val c = outcome.candidate
                log("🧪 Parsed: amount=${c.amount} currency='${c.currency}' merchant='${c.merchant}' confidence=${"%.2f".format(c.confidence)}")
            }
            is ParseOutcome.NotPayment ->
                log("🧪 Not a payment (${outcome.reason}), confidence=${"%.2f".format(outcome.confidence)}")
        }
    }

    fun createFakePayment() {
        viewModelScope.launch {
            val candidate = PaymentCandidate(
                amount = (5..80).random() + 0.5,
                currency = "JOD",
                merchant = listOf("Coffee Shop", "SuperMart", "Gas Station", null).random(),
                sender = "DEBUG",
                originalMessage = "Debug payment created at ${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                confidence = 1f,
            )
            when (val result = container.paymentRepository.ingest(candidate)) {
                is IngestResult.Inserted -> log("✅ Fake payment #${result.paymentId} created")
                IngestResult.Duplicate -> log("♻️ Duplicate fake payment")
            }
        }
    }

    fun triggerBubble() {
        viewModelScope.launch {
            val candidate = PaymentCandidate(
                amount = 12.5,
                currency = "JOD",
                merchant = "Coffee Shop",
                sender = "DEBUG",
                originalMessage = "Bubble test at ${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                confidence = 1f,
            )
            when (val result = container.paymentRepository.ingest(candidate)) {
                is IngestResult.Inserted -> {
                    log("🫧 Triggering bubble for payment #${result.paymentId}")
                    BubbleLauncher.launchIfPossible(getApplication(), result.paymentId)
                }
                IngestResult.Duplicate -> log("♻️ Duplicate — bubble not triggered")
            }
        }
    }

    fun testApi() {
        viewModelScope.launch {
            val dto = PaymentDto(
                amount = 1.0,
                currency = "JOD",
                merchant = "API Test",
                category = "Other",
                timestamp = System.currentTimeMillis(),
                sender = "DEBUG",
                originalMessage = "API connectivity test",
            )
            when (val result = container.apiClient.postPayment(dto)) {
                ApiResult.Success -> log("🌐 API OK: POST /payments succeeded")
                is ApiResult.Failure -> log("🌐 API failed: ${result.message} (retryable=${result.retryable})")
                ApiResult.Disabled -> log("🌐 API disabled or no base URL set (see Settings → Server)")
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val summary = container.syncPayments()
            log("🔄 Sync: synced=${summary.synced} failed=${summary.failed} disabled=${summary.disabled}")
            SyncScheduler.schedule(getApplication())
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            container.paymentRepository.clearAll()
            log("🗑️ All payments deleted")
        }
    }
}
