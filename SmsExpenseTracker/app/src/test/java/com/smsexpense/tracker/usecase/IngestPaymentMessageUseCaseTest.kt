package com.smsexpense.tracker.usecase

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestPaymentMessageUseCaseTest {

    private val payments = FakePaymentRepository()
    private val settings = FakeSettingsRepository(senderIdsInitial = setOf("MYBANK"))
    private val useCase = IngestPaymentMessageUseCase(SmsParser(), payments, settings)

    private fun message(
        body: String = "تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop",
        sender: String = "MYBANK",
        timestamp: Long = 1_722_988_800_000,
    ) = IncomingMessage(sender, body, timestamp)

    @Test
    fun `valid bank payment is saved`() = runTest {
        val outcome = useCase(message())
        assertTrue(outcome is IngestOutcome.PaymentSaved)
        val saved = payments.all().single()
        assertEquals(12.5, saved.amount, 0.0001)
        assertEquals("JOD", saved.currency)
        assertEquals("Coffee Shop", saved.merchant)
        assertEquals(PaymentStatus.UNCATEGORIZED, saved.status)
    }

    @Test
    fun `unknown sender is rejected`() = runTest {
        val outcome = useCase(message(sender = "SPAMMER"))
        assertTrue(outcome is IngestOutcome.NotFromBank)
        assertTrue(payments.all().isEmpty())
    }

    @Test
    fun `sender match is case-insensitive`() = runTest {
        val outcome = useCase(message(sender = "myBank"))
        assertTrue(outcome is IngestOutcome.PaymentSaved)
    }

    @Test
    fun `duplicate message creates exactly one payment`() = runTest {
        val first = useCase(message())
        val second = useCase(message())
        assertTrue(first is IngestOutcome.PaymentSaved)
        assertEquals(IngestOutcome.DuplicateIgnored, second)
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `salary message is not ingested`() = runTest {
        val outcome = useCase(message(body = "تم تحويل راتبك بقيمة 500 دينار"))
        assertTrue(outcome is IngestOutcome.NotAPayment)
        assertTrue(payments.all().isEmpty())
    }

    @Test
    fun `below-threshold payment is rejected`() = runTest {
        settings.setConfidenceThreshold(0.99f)
        val outcome = useCase(message(body = "Payment of 5.00"))
        assertTrue("got $outcome", outcome is IngestOutcome.BelowThreshold)
    }

    @Test
    fun `missing currency falls back to default currency`() = runTest {
        settings.setDefaultCurrency("SAR")
        val outcome = useCase(message(body = "تم استخدام البطاقة بمبلغ 32.00"))
        assertTrue(outcome is IngestOutcome.PaymentSaved)
        assertEquals("SAR", payments.all().single().currency)
    }

    @Test
    fun `sender filter can be skipped for debug simulation`() = runTest {
        val outcome = useCase(message(sender = "UNKNOWN"), skipSenderFilter = true)
        assertTrue(outcome is IngestOutcome.PaymentSaved)
    }
}
