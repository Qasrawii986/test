package com.smsexpense.tracker.parser

import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.parser.DedupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupKeyTest {

    private fun candidate(
        sender: String = "MYBANK",
        message: String = "تم خصم 12.50 JOD من بطاقتك",
        timestamp: Long = 1_722_988_800_000,
        amount: Double = 12.5,
    ) = PaymentCandidate(
        amount = amount,
        currency = "JOD",
        merchant = null,
        sender = sender,
        originalMessage = message,
        timestamp = timestamp,
        confidence = 1f,
    )

    @Test
    fun `identical candidates produce identical keys`() {
        assertEquals(DedupKey.of(candidate()), DedupKey.of(candidate()))
    }

    @Test
    fun `sender case and whitespace do not change the key`() {
        assertEquals(
            DedupKey.of(candidate(sender = "MYBANK")),
            DedupKey.of(candidate(sender = "  mybank ")),
        )
    }

    @Test
    fun `different timestamp changes the key`() {
        assertNotEquals(
            DedupKey.of(candidate(timestamp = 1_722_988_800_000)),
            DedupKey.of(candidate(timestamp = 1_722_988_860_000)),
        )
    }

    @Test
    fun `different message changes the key`() {
        assertNotEquals(
            DedupKey.of(candidate(message = "A")),
            DedupKey.of(candidate(message = "B")),
        )
    }

    @Test
    fun `different amount changes the key`() {
        assertNotEquals(
            DedupKey.of(candidate(amount = 12.5)),
            DedupKey.of(candidate(amount = 13.5)),
        )
    }
}
