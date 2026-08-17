package com.smsexpense.tracker.usecase

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A contactless tap produces a wallet notification and, minutes later, a bank
 * SMS about the same purchase. The wording differs completely, so only amount
 * and proximity in time can link them.
 */
class CrossSourceDedupTest {

    private val payments = FakePaymentRepository()
    private val settings = FakeSettingsRepository(senderIdsInitial = setOf("MYBANK"))
    private val useCase = IngestPaymentMessageUseCase(SmsParser(), payments, settings)

    private val base = 1_720_000_000_000L
    private val minute = 60_000L

    private fun walletNotification(amount: String, at: Long) = IncomingMessage(
        sender = "Samsung Pay",
        body = "Purchase of JOD $amount at ghassan ah",
        timestamp = at,
    )

    private fun bankSms(amount: String, at: Long) = IncomingMessage(
        sender = "MYBANK",
        body = "$amount JOD at ghassan ah. Card 797. Available balance: 607.511 JOD.",
        timestamp = at,
    )

    @Test
    fun `wallet notification then bank sms records one payment`() = runTest {
        val first = useCase(
            walletNotification("21.428", base),
            skipSenderFilter = true,
            source = PaymentSource.NOTIFICATION,
        )
        val second = useCase(bankSms("21.428", base + 2 * minute), source = PaymentSource.SMS_REALTIME)

        assertTrue(first is IngestOutcome.PaymentSaved)
        assertEquals(IngestOutcome.DuplicateIgnored, second)
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `bank sms first then wallet notification also records one payment`() = runTest {
        useCase(bankSms("21.428", base), source = PaymentSource.SMS_REALTIME)
        val second = useCase(
            walletNotification("21.428", base + minute),
            skipSenderFilter = true,
            source = PaymentSource.NOTIFICATION,
        )

        assertEquals(IngestOutcome.DuplicateIgnored, second)
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `same amount far apart in time stays two payments`() = runTest {
        useCase(
            walletNotification("21.428", base),
            skipSenderFilter = true,
            source = PaymentSource.NOTIFICATION,
        )
        useCase(bankSms("21.428", base + 60 * minute), source = PaymentSource.SMS_REALTIME)
        assertEquals(2, payments.all().size)
    }

    @Test
    fun `two genuine purchases of the same amount on one channel both count`() = runTest {
        // Same source, different text/time: the cross-source rule must not merge these.
        useCase(bankSms("2.500", base), source = PaymentSource.SMS_REALTIME)
        useCase(
            IncomingMessage("MYBANK", "2.500 JOD at Coffee Shop. Card 797.", base + minute),
            source = PaymentSource.SMS_REALTIME,
        )
        assertEquals(2, payments.all().size)
    }

    @Test
    fun `a re-posted notification does not create a second payment`() = runTest {
        val message = walletNotification("21.428", base)
        useCase(message, skipSenderFilter = true, source = PaymentSource.NOTIFICATION)
        // Same notification updated in place: new timestamp, identical text.
        val repost = message.copy(timestamp = base + 30_000L)
        val second = useCase(repost, skipSenderFilter = true, source = PaymentSource.NOTIFICATION)

        assertEquals(IngestOutcome.DuplicateIgnored, second)
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `notification payments are stored with the notification source`() = runTest {
        useCase(
            walletNotification("21.428", base),
            skipSenderFilter = true,
            source = PaymentSource.NOTIFICATION,
        )
        assertEquals(PaymentSource.NOTIFICATION, payments.all().single().source)
        assertEquals("Samsung Pay", payments.all().single().sender)
    }
}
