package com.smsexpense.tracker.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.R
import com.smsexpense.tracker.util.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLocaleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppLocale.prime(AppLocale.SYSTEM)
    }

    @Test
    fun `arabic resources resolve when the language is arabic`() {
        AppLocale.prime(AppLocale.ARABIC)
        val localized = AppLocale.wrap(context)
        assertEquals("الإعدادات", localized.getString(R.string.settings))
        assertEquals("غير مصنّفة", localized.getString(R.string.uncategorized))
    }

    @Test
    fun `english resources resolve when the language is english`() {
        AppLocale.prime(AppLocale.ENGLISH)
        val localized = AppLocale.wrap(context)
        assertEquals("Settings", localized.getString(R.string.settings))
    }

    @Test
    fun `the two languages actually differ`() {
        AppLocale.prime(AppLocale.ARABIC)
        val arabic = AppLocale.wrap(context).getString(R.string.dashboard_total_spending)
        AppLocale.prime(AppLocale.ENGLISH)
        val english = AppLocale.wrap(context).getString(R.string.dashboard_total_spending)
        assertNotEquals(arabic, english)
    }

    @Test
    fun `system default does not wrap the context`() {
        AppLocale.prime(AppLocale.SYSTEM)
        assertEquals(context, AppLocale.wrap(context))
    }

    @Test
    fun `rtl is reported only for arabic`() {
        AppLocale.prime(AppLocale.ARABIC)
        assertTrue(AppLocale.isRtl())
        AppLocale.prime(AppLocale.ENGLISH)
        assertFalse(AppLocale.isRtl())
        AppLocale.prime(AppLocale.SYSTEM)
        assertFalse(AppLocale.isRtl())
    }

    @Test
    fun `formatted strings keep their arguments in both languages`() {
        listOf(AppLocale.ENGLISH, AppLocale.ARABIC).forEach { language ->
            AppLocale.prime(language)
            val text = AppLocale.wrap(context).getString(R.string.dashboard_transactions, 7)
            assertTrue("$language dropped the count: $text", text.contains("7"))
        }
    }

    @Test
    fun `supported languages are exactly the ones the picker offers`() {
        assertEquals(
            listOf(AppLocale.SYSTEM, AppLocale.ENGLISH, AppLocale.ARABIC),
            AppLocale.SUPPORTED,
        )
    }
}
