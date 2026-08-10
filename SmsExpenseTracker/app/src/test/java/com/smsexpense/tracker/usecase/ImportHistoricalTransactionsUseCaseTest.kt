package com.smsexpense.tracker.usecase

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.ImportHistoricalTransactionsUseCase
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.domain.usecase.ScanEvent
import com.smsexpense.tracker.fakes.FakeDeviceSmsSource
import com.smsexpense.tracker.fakes.FakeImportHistoryRepository
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportHistoricalTransactionsUseCaseTest {

    private val payments = FakePaymentRepository()
    private val settings = FakeSettingsRepository(senderIdsInitial = setOf("MYBANK"))
    private val smsSource = FakeDeviceSmsSource()
    private val history = FakeImportHistoryRepository()
    private val useCase = ImportHistoricalTransactionsUseCase(
        smsSource, SmsParser(), payments, settings, history,
    )

    private val day = 24 * 60 * 60 * 1000L
    private val base = 1_720_000_000_000L

    private fun paymentSms(ts: Long, amount: String = "12.50", sender: String = "MYBANK") =
        IncomingMessage(sender, "تم خصم $amount JOD من بطاقتك لدى Coffee Shop", ts)

    private suspend fun scanAll(from: Long = 0, to: Long = Long.MAX_VALUE, senders: Set<String> = setOf("MYBANK")) =
        useCase.scan(from, to, senders).last() as ScanEvent.Done

    @Test
    fun `scan filters by date range`() = runTest {
        smsSource.inbox = listOf(
            paymentSms(base - 10 * day, "1.00"),
            paymentSms(base, "2.00"),
            paymentSms(base + 10 * day, "3.00"),
        )
        val done = scanAll(from = base - day, to = base + day)
        assertEquals(1, done.candidates.size)
        assertEquals(2.0, done.candidates.single().candidate.amount, 0.0001)
    }

    @Test
    fun `scan filters by sender case-insensitively`() = runTest {
        smsSource.inbox = listOf(
            paymentSms(base, sender = "myBank"),
            paymentSms(base + 1, sender = "OTHERBANK"),
            IncomingMessage("Friend", "تم خصم 5.00 JOD من بطاقتك", base + 2),
        )
        val done = scanAll()
        assertEquals(1, done.candidates.size)
        assertEquals("myBank", done.candidates.single().candidate.sender)
    }

    @Test
    fun `scan uses the same parser and skips non-payments`() = runTest {
        smsSource.inbox = listOf(
            paymentSms(base),
            IncomingMessage("MYBANK", "تم تحويل راتبك بقيمة 500 دينار", base + 1),
            IncomingMessage("MYBANK", "رمز التحقق الخاص بك هو 123456", base + 2),
            IncomingMessage("MYBANK", "Hello, your statement is ready", base + 3),
        )
        val done = scanAll()
        assertEquals(1, done.candidates.size)
    }

    @Test
    fun `scan applies default currency fallback`() = runTest {
        settings.setDefaultCurrency("SAR")
        smsSource.inbox = listOf(
            IncomingMessage("MYBANK", "تم استخدام البطاقة بمبلغ 32.00", base)
        )
        val done = scanAll()
        assertEquals("SAR", done.candidates.single().candidate.currency)
    }

    @Test
    fun `import saves selected with categories and records history`() = runTest {
        smsSource.inbox = listOf(paymentSms(base, "10.00"), paymentSms(base + 1, "20.00"))
        val done = scanAll()
        val summary = useCase.import(
            selection = listOf(
                done.candidates[0].candidate to 7L,
                done.candidates[1].candidate to null,
            ),
            fromDate = 0,
            toDate = Long.MAX_VALUE,
        )
        assertEquals(2, summary.imported)
        assertEquals(30.0, summary.total, 0.0001)

        val saved = payments.all()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.source == PaymentSource.SMS_HISTORICAL })
        val categorized = saved.first { it.categoryId == 7L }
        assertEquals(PaymentStatus.CATEGORIZED, categorized.status)
        val uncategorized = saved.first { it.categoryId == null }
        assertEquals(PaymentStatus.UNCATEGORIZED, uncategorized.status)

        assertEquals(1, history.observeAll().first().size)
    }

    // --- THE critical test: re-import of the same period yields zero new rows ---
    @Test
    fun `re-scanning the same period after import finds zero new transactions`() = runTest {
        smsSource.inbox = (0 until 37).map { paymentSms(base + it * day, "${it + 1}.50") }

        val firstScan = scanAll()
        assertEquals(37, firstScan.candidates.size)
        val summary = useCase.import(
            firstScan.candidates.map { it.candidate to null }, 0, Long.MAX_VALUE,
        )
        assertEquals(37, summary.imported)

        val secondScan = scanAll()
        assertEquals(0, secondScan.candidates.size)
        assertEquals(37, secondScan.alreadyImported)
        assertEquals(37, payments.all().size) // never 74
    }

    @Test
    fun `importing the same selection twice skips duplicates`() = runTest {
        smsSource.inbox = listOf(paymentSms(base))
        val done = scanAll()
        val selection = done.candidates.map { it.candidate to null }

        val first = useCase.import(selection, 0, Long.MAX_VALUE)
        val second = useCase.import(selection, 0, Long.MAX_VALUE)

        assertEquals(1, first.imported)
        assertEquals(0, second.imported)
        assertEquals(1, second.duplicatesSkipped)
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `message already captured by realtime receiver is excluded despite timestamp drift`() = runTest {
        // Realtime capture stores the SMSC timestamp...
        val ingest = IngestPaymentMessageUseCase(SmsParser(), payments, settings)
        ingest(paymentSms(base))
        // ...while the inbox stores the received-at timestamp (1h later here).
        smsSource.inbox = listOf(paymentSms(base + 60 * 60 * 1000L))

        val done = scanAll()
        assertEquals(0, done.candidates.size)
        assertEquals(1, done.alreadyImported)
    }

    @Test
    fun `unparseable messages never crash the scan`() = runTest {
        smsSource.inbox = listOf(
            IncomingMessage("MYBANK", "", base),
            paymentSms(base + 1),
        )
        val done = scanAll()
        assertEquals(1, done.candidates.size)
    }
}
