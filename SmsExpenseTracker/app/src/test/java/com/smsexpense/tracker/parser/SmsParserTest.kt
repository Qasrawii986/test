package com.smsexpense.tracker.parser

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.TransactionDirection
import com.smsexpense.tracker.domain.model.TransactionType
import com.smsexpense.tracker.domain.model.direction
import com.smsexpense.tracker.domain.model.isExpense
import com.smsexpense.tracker.domain.parser.ParseOutcome
import com.smsexpense.tracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val parser = SmsParser()

    private fun parse(body: String, sender: String = "MYBANK"): ParseOutcome =
        parser.parse(IncomingMessage(sender = sender, body = body, timestamp = 1_722_988_800_000))

    private fun expectPayment(body: String): com.smsexpense.tracker.domain.model.PaymentCandidate {
        val outcome = parse(body)
        assertTrue("Expected payment for: $body, got $outcome", outcome is ParseOutcome.Payment)
        return (outcome as ParseOutcome.Payment).candidate
    }

    private fun expectNotPayment(body: String): ParseOutcome.NotPayment {
        val outcome = parse(body)
        assertTrue("Expected NOT payment for: $body, got $outcome", outcome is ParseOutcome.NotPayment)
        return outcome as ParseOutcome.NotPayment
    }

    // ============ CliQ transfers (bug fix) ============

    private val cliqOutSms = "13.000 JOD CliQ transfer to Abdulraheem Rizk.\nAvailable balance: 594.511 JOD."

    // Test 1 — CliQ outgoing is a payment with full extraction
    @Test
    fun `cliq outgoing transfer is an expense with recipient as counterparty`() {
        val c = expectPayment(cliqOutSms)
        assertEquals(TransactionType.CLIQ_TRANSFER_OUT, c.type)
        assertEquals(13.0, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
        assertEquals("Abdulraheem Rizk", c.merchant)
        assertTrue(c.type.isExpense)
        assertEquals(TransactionDirection.OUTGOING, c.type.direction)
    }

    // Test 5 — the balance must never be mistaken for the amount
    @Test
    fun `available balance is never extracted as the transaction amount`() {
        val c = expectPayment(cliqOutSms)
        assertEquals(13.0, c.amount, 0.0001)
        assertTrue(c.amount != 594.511)
    }

    @Test
    fun `cliq casing variants all parse`() {
        for (variant in listOf("CliQ", "CLIQ", "cliq", "Cliq")) {
            val c = expectPayment("25.00 JOD $variant transfer to Ahmad.")
            assertEquals(TransactionType.CLIQ_TRANSFER_OUT, c.type)
            assertEquals(25.0, c.amount, 0.0001)
            assertEquals("Ahmad", c.merchant)
        }
    }

    @Test
    fun `cliq with currency before amount parses`() {
        val c = expectPayment("JOD 10.500 CliQ transfer to Ahmad.")
        assertEquals(10.5, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
        assertEquals("Ahmad", c.merchant)
    }

    @Test
    fun `arabic cliq transfer parses`() {
        val c = expectPayment("تم تحويل 13.000 دينار عبر CliQ إلى محمد خالد")
        assertEquals(TransactionType.CLIQ_TRANSFER_OUT, c.type)
        assertEquals(13.0, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
        assertEquals("محمد خالد", c.merchant)
    }

    @Test
    fun `arabic cliq with kaleek spelling parses`() {
        val c = expectPayment("تم تحويل مبلغ 13.000 JOD عبر كليك إلى سامر")
        assertEquals(TransactionType.CLIQ_TRANSFER_OUT, c.type)
        assertEquals("سامر", c.merchant)
    }

    // Test 4 — incoming CliQ is NOT an expense
    @Test
    fun `incoming cliq transfer is not an expense`() {
        val outcome = expectNotPayment("13.000 JOD CliQ transfer from Ahmad.\nAvailable balance: 620.511 JOD.")
        assertEquals(TransactionType.INCOMING_TRANSFER, outcome.type)
        assertFalse(outcome.type.isExpense)
        assertEquals(TransactionDirection.INCOMING, outcome.type.direction)
    }

    // Test 2 — the real OTP message must never become a 108.92 SAR expense
    @Test
    fun `otp with amount is classified as OTP not a payment`() {
        val outcome = expectNotPayment(
            "554794 هو رمز التأكيد OTP لتنفيذ حركة شراء بقيمة SAR 108.92 من ghassan ah " +
                "ببطاقتك المنتهية بالأرقام 6797. لا تشارك هذا الرمز مع أحد"
        )
        assertEquals(TransactionType.OTP, outcome.type)
    }

    @Test
    fun `arabic authorization code without the word OTP is still rejected`() {
        val outcome = expectNotPayment("رمز التأكيد لعملية الشراء بقيمة 50.00 JOD هو 112233")
        assertEquals(TransactionType.OTP, outcome.type)
    }

    // Test 3 — the real card-purchase format keeps working
    @Test
    fun `card purchase with balance line still parses correctly`() {
        val c = expectPayment("21.428 JOD at ghassan ah.\nCard 797.\nAvailable balance: 607.511 JOD.")
        assertEquals(TransactionType.CARD_PURCHASE, c.type)
        assertEquals(21.428, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
        assertEquals("ghassan ah", c.merchant)
        assertTrue(c.type.isExpense)
    }

    @Test
    fun `balance-only notification is not a transaction`() {
        val outcome = expectNotPayment("Available balance: 594.511 JOD.")
        assertEquals(TransactionType.BALANCE_UPDATE, outcome.type)
    }

    // --- 1. Arabic payment ---
    @Test
    fun `arabic debit with latin currency`() {
        val c = expectPayment("تم خصم 12.50 JOD من بطاقتك")
        assertEquals(12.5, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
    }

    // --- 2. Arabic purchase with arabic currency word ---
    @Test
    fun `arabic purchase with dinar word`() {
        val c = expectPayment("تمت عملية شراء بقيمة 15.750 دينار")
        assertEquals(15.75, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
    }

    // --- 3. English payment, currency before amount ---
    @Test
    fun `english purchase currency first`() {
        val c = expectPayment("Purchase of JOD 25.00")
        assertEquals(25.0, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
    }

    // --- 4. English charged ---
    @Test
    fun `english card charged`() {
        val c = expectPayment("Your card was charged 18.50 JOD")
        assertEquals(18.5, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
    }

    // --- 5. Card used, no currency in message ---
    @Test
    fun `card used without currency falls back to placeholder`() {
        val c = expectPayment("تم استخدام البطاقة بمبلغ 32.00")
        assertEquals(32.0, c.amount, 0.0001)
        assertEquals(SmsParser.DEFAULT_CURRENCY_PLACEHOLDER, c.currency)
    }

    // --- Different currencies ---
    @Test
    fun `saudi riyal in arabic`() {
        val c = expectPayment("تم خصم 50 ريال من بطاقتك")
        assertEquals(50.0, c.amount, 0.0001)
        assertEquals("SAR", c.currency)
    }

    @Test
    fun `euro with decimal comma separator`() {
        val c = expectPayment("Purchase of 12,50 EUR at Berlin Cafe")
        assertEquals(12.5, c.amount, 0.0001)
        assertEquals("EUR", c.currency)
    }

    @Test
    fun `usd payment`() {
        val c = expectPayment("Your card was charged USD 99.99")
        assertEquals(99.99, c.amount, 0.0001)
        assertEquals("USD", c.currency)
    }

    // --- Thousands separators ---
    @Test
    fun `amount with thousands separator`() {
        val c = expectPayment("تم خصم 1,245.50 JOD من بطاقتك")
        assertEquals(1245.5, c.amount, 0.0001)
    }

    // --- Arabic-Indic digits ---
    @Test
    fun `arabic indic digits with arabic decimal separator`() {
        val c = expectPayment("تم خصم ١٢٫٥٠ دينار من بطاقتك")
        assertEquals(12.5, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
    }

    // --- Multipart: long concatenated body still parses ---
    @Test
    fun `long multipart-style message parses`() {
        val part1 = "عزيزي العميل، نود إعلامكم بأنه قد تمت عملية شراء عبر نقاط البيع "
        val part2 = "بقيمة 47.250 دينار لدى Carrefour Amman بتاريخ 09-08-2026 الساعة 20:30"
        val c = expectPayment(part1 + part2)
        assertEquals(47.25, c.amount, 0.0001)
        assertEquals("JOD", c.currency)
        assertEquals("Carrefour Amman", c.merchant)
    }

    // --- Merchant extraction ---
    @Test
    fun `merchant after arabic lada`() {
        val c = expectPayment("تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop")
        assertEquals("Coffee Shop", c.merchant)
    }

    @Test
    fun `merchant after english at`() {
        val c = expectPayment("Purchase of JOD 25.00 at SuperMart using card ending 1234")
        assertEquals("SuperMart", c.merchant)
    }

    @Test
    fun `payment without merchant is still a payment`() {
        val c = expectPayment("تم خصم 9.99 JOD من بطاقتك")
        assertNull(c.merchant)
    }

    // --- Negative patterns ---
    @Test
    fun `salary transfer is not a payment`() {
        expectNotPayment("تم تحويل راتبك بقيمة 500 دينار الى حسابك")
    }

    @Test
    fun `incoming transfer is not a payment`() {
        expectNotPayment("تم تحويل مبلغ 100 دينار إلى حسابك")
    }

    @Test
    fun `deposit is not a payment`() {
        expectNotPayment("تم إيداع 200 دينار في حسابك")
    }

    @Test
    fun `otp is not a payment`() {
        expectNotPayment("رمز التحقق الخاص بك هو 123456 لا تشاركه مع أحد")
    }

    @Test
    fun `english otp is not a payment`() {
        expectNotPayment("Your OTP verification code is 445566")
    }

    @Test
    fun `refund is not a payment`() {
        expectNotPayment("Refund of JOD 25.00 was credited to your card")
    }

    // --- Unknown formats / junk ---
    @Test
    fun `unknown format is not a payment`() {
        expectNotPayment("Hello! Your monthly statement is ready.")
    }

    @Test
    fun `empty message is not a payment`() {
        expectNotPayment("")
    }

    @Test
    fun `payment words without amount is not a payment`() {
        expectNotPayment("تم خصم رسوم الخدمة من حسابك")
    }

    // --- Confidence ---
    @Test
    fun `confident payment scores high`() {
        val c = expectPayment("تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop")
        assertTrue("confidence was ${c.confidence}", c.confidence >= 0.8f)
    }

    @Test
    fun `confidence is preserved on candidate`() {
        val c = expectPayment("Purchase of JOD 25.00")
        assertTrue(c.confidence in 0.5f..1f)
    }
}
