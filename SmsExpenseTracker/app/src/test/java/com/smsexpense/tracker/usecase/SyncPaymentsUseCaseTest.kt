package com.smsexpense.tracker.usecase

import com.smsexpense.tracker.data.remote.api.ApiResult
import com.smsexpense.tracker.data.remote.api.PaymentApiClient
import com.smsexpense.tracker.data.remote.dto.PaymentDto
import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.SyncStatus
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.domain.usecase.SyncPaymentsUseCase
import com.smsexpense.tracker.fakes.FakeCategoryRepository
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPaymentsUseCaseTest {

    private val payments = FakePaymentRepository()
    private val categories = FakeCategoryRepository()
    private val settings = FakeSettingsRepository()
    private val ingest = IngestPaymentMessageUseCase(SmsParser(), payments, settings)

    private class RecordingApiClient(var result: ApiResult) : PaymentApiClient {
        val posted = mutableListOf<PaymentDto>()
        override suspend fun postPayment(payment: PaymentDto): ApiResult {
            posted.add(payment)
            return result
        }
    }

    @Test
    fun `successful sync marks payments SYNCED`() = runTest {
        ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1))
        ingest(IncomingMessage("MYBANK", "Purchase of JOD 25.00", 2))
        val api = RecordingApiClient(ApiResult.Success)

        val summary = SyncPaymentsUseCase(payments, categories, api)()

        assertEquals(2, summary.synced)
        assertEquals(0, summary.failed)
        assertTrue(payments.all().all { it.syncStatus == SyncStatus.SYNCED })
        assertEquals(2, api.posted.size)
    }

    @Test
    fun `failed sync marks payments FAILED and they stay pending`() = runTest {
        ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1))
        val api = RecordingApiClient(ApiResult.Failure("boom", retryable = true))

        val summary = SyncPaymentsUseCase(payments, categories, api)()

        assertEquals(1, summary.failed)
        assertTrue(payments.all().all { it.syncStatus == SyncStatus.FAILED })
        // FAILED payments are retried on the next run.
        api.result = ApiResult.Success
        val retry = SyncPaymentsUseCase(payments, categories, api)()
        assertEquals(1, retry.synced)
    }

    @Test
    fun `disabled api stops without changing statuses`() = runTest {
        ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1))
        val api = RecordingApiClient(ApiResult.Disabled)

        val summary = SyncPaymentsUseCase(payments, categories, api)()

        assertTrue(summary.disabled)
        assertTrue(payments.all().all { it.syncStatus == SyncStatus.PENDING })
    }

    @Test
    fun `category name is resolved into the DTO`() = runTest {
        val outcome = ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1))
        val paymentId = (outcome as com.smsexpense.tracker.domain.usecase.IngestOutcome.PaymentSaved).paymentId
        val categoryId = categories.add("Food", "🍔", null)
        payments.categorize(paymentId, categoryId)
        val api = RecordingApiClient(ApiResult.Success)

        SyncPaymentsUseCase(payments, categories, api)()

        assertEquals("Food", api.posted.single().category)
    }
}
