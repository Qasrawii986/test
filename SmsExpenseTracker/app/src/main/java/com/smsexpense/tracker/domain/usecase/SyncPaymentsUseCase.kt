package com.smsexpense.tracker.domain.usecase

import com.smsexpense.tracker.data.remote.api.ApiResult
import com.smsexpense.tracker.data.remote.api.PaymentApiClient
import com.smsexpense.tracker.data.remote.dto.PaymentDto
import com.smsexpense.tracker.domain.model.SyncStatus
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.PaymentRepository

data class SyncSummary(val synced: Int, val failed: Int, val disabled: Boolean)

/**
 * Pushes every PENDING/FAILED payment to the backend. Safe to call with the API
 * disabled (returns immediately) and safe to re-run: rows flip to SYNCED once accepted.
 */
class SyncPaymentsUseCase(
    private val paymentRepository: PaymentRepository,
    private val categoryRepository: CategoryRepository,
    private val apiClient: PaymentApiClient,
) {
    suspend operator fun invoke(): SyncSummary {
        val pending = paymentRepository.pendingSync()
        if (pending.isEmpty()) return SyncSummary(0, 0, disabled = false)

        val categories = categoryRepository.getAll().associateBy { it.id }
        var synced = 0
        var failed = 0
        for (payment in pending) {
            val dto = PaymentDto(
                amount = payment.amount,
                currency = payment.currency,
                merchant = payment.merchant,
                category = payment.categoryId?.let { categories[it]?.name },
                timestamp = payment.timestamp,
                sender = payment.sender,
                originalMessage = payment.originalMessage,
            )
            when (apiClient.postPayment(dto)) {
                ApiResult.Success -> {
                    paymentRepository.markSync(payment.id, SyncStatus.SYNCED)
                    synced++
                }
                is ApiResult.Failure -> {
                    paymentRepository.markSync(payment.id, SyncStatus.FAILED)
                    failed++
                }
                ApiResult.Disabled -> return SyncSummary(synced, failed, disabled = true)
            }
        }
        return SyncSummary(synced, failed, disabled = false)
    }
}
