package com.smsexpense.tracker.split

import com.smsexpense.tracker.domain.model.Allocation
import com.smsexpense.tracker.domain.model.PaymentSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split arithmetic, which the dashboard headline and every report depend on.
 * The rule under test: your share is the total minus what you charged to others,
 * so a payment with no allocations is entirely yours.
 */
class PaymentSplitTest {

    private fun split(total: Double, vararg amounts: Pair<Long, Double>) = PaymentSplit(
        paymentId = 1L,
        total = total,
        currency = "JOD",
        allocations = amounts.mapIndexed { index, (payerId, amount) ->
            Allocation(
                id = index + 1L,
                paymentId = 1L,
                payerId = payerId,
                amount = amount,
                settled = false,
            )
        },
    )

    @Test
    fun `a payment with no allocations is entirely yours`() {
        val s = split(100.0)
        assertTrue(s.isFullyMine)
        assertEquals(100.0, s.myShare, 0.0001)
        assertEquals(0.0, s.chargedToOthers, 0.0001)
        assertFalse(s.isFullyCharged)
    }

    @Test
    fun `charging the whole amount leaves you nothing`() {
        val s = split(100.0, 2L to 100.0)
        assertFalse(s.isFullyMine)
        assertEquals(0.0, s.myShare, 0.0001)
        assertTrue(s.isFullyCharged)
    }

    @Test
    fun `a partial charge leaves the remainder as your share`() {
        val s = split(100.0, 2L to 60.0)
        assertEquals(60.0, s.chargedToOthers, 0.0001)
        assertEquals(40.0, s.myShare, 0.0001)
        assertFalse(s.isFullyCharged)
    }

    @Test
    fun `several payers add up`() {
        val s = split(120.0, 2L to 50.0, 3L to 30.0)
        assertEquals(80.0, s.chargedToOthers, 0.0001)
        assertEquals(40.0, s.myShare, 0.0001)
        assertEquals(50.0, s.amountFor(2L), 0.0001)
        assertEquals(30.0, s.amountFor(3L), 0.0001)
        assertEquals(0.0, s.amountFor(99L), 0.0001)
    }

    @Test
    fun `over allocation is reported and never produces a negative share`() {
        val s = split(100.0, 2L to 130.0)
        assertTrue(s.isOverAllocated)
        // A negative "your share" would corrupt every total that adds it up.
        assertEquals(0.0, s.myShare, 0.0001)
    }

    @Test
    fun `rounding noise does not count as over allocation`() {
        // 100 / 3 twice plus your own third: the two saved parts cannot sum exactly.
        val third = 100.0 / 3
        val s = split(100.0, 2L to third, 3L to third)
        assertFalse(s.isOverAllocated)
        assertEquals(third, s.myShare, 0.001)
    }

    @Test
    fun `an amount edited down below what others owe clamps to zero`() {
        // The bubble lets you correct the amount after a split was already saved.
        val s = split(20.0, 2L to 60.0)
        assertEquals(0.0, s.myShare, 0.0001)
        assertTrue(s.isOverAllocated)
    }
}
