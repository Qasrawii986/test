package com.smsexpense.tracker.parser

import com.smsexpense.tracker.domain.parser.AmountNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountNormalizerTest {

    @Test
    fun `plain decimal`() {
        assertEquals(12.5, AmountNormalizer.parseAmount("12.50")!!, 0.0001)
    }

    @Test
    fun `three decimal digits (JOD fils)`() {
        assertEquals(15.75, AmountNormalizer.parseAmount("15.750")!!, 0.0001)
    }

    @Test
    fun `integer amount`() {
        assertEquals(32.0, AmountNormalizer.parseAmount("32")!!, 0.0001)
    }

    @Test
    fun `decimal comma`() {
        assertEquals(12.5, AmountNormalizer.parseAmount("12,50")!!, 0.0001)
    }

    @Test
    fun `thousands with dot decimals`() {
        assertEquals(1245.5, AmountNormalizer.parseAmount("1,245.50")!!, 0.0001)
    }

    @Test
    fun `european thousands with comma decimals`() {
        assertEquals(1234.56, AmountNormalizer.parseAmount("1.234,56")!!, 0.0001)
    }

    @Test
    fun `comma thousands without decimals`() {
        assertEquals(1245.0, AmountNormalizer.parseAmount("1,245")!!, 0.0001)
    }

    @Test
    fun `arabic indic digits`() {
        assertEquals(12.5, AmountNormalizer.parseAmount("١٢٫٥٠")!!, 0.0001)
    }

    @Test
    fun `extended arabic indic digits`() {
        assertEquals(45.0, AmountNormalizer.parseAmount("۴۵")!!, 0.0001)
    }

    @Test
    fun `garbage returns null`() {
        assertNull(AmountNormalizer.parseAmount("abc"))
        assertNull(AmountNormalizer.parseAmount(""))
    }

    @Test
    fun `zero returns null`() {
        assertNull(AmountNormalizer.parseAmount("0"))
        assertNull(AmountNormalizer.parseAmount("0.00"))
    }
}
