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

    @org.junit.Before
    fun setUp() {
        AppLocale.prime(AppLocale.SYSTEM)
    }

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
    fun `every screen's strings resolve in arabic, including services`() {
        AppLocale.prime(AppLocale.ARABIC)
        val ar = AppLocale.wrap(context)
        // One string from each area that was wired up, so a missed translation or a
        // wrong resource id fails here instead of surfacing as English on a screen.
        listOf(
            R.string.import_title, R.string.picker_title, R.string.updates,
            R.string.quick_launch_title, R.string.notif_source_title, R.string.debug_title,
            R.string.ql_tile_title, R.string.ql_sensor_warning, R.string.blocker_no_overlay,
            R.string.backtap_notification_text, R.string.panel_notification_title,
            R.string.appearance_shape_circle, R.string.position_snap,
            R.string.payers_title, R.string.payers_subtitle, R.string.payer_you,
            R.string.split_title, R.string.split_who_pays, R.string.split_your_share,
            R.string.split_evenly, R.string.payer_settle, R.string.edit_details,
            R.string.edit_merchant, R.string.edit_amount, R.string.dashboard_your_share,
            R.string.dashboard_owed_title, R.string.settings_payers,
            R.string.appearance_background, R.string.appearance_background_pick,
            R.string.settings_autohide_never, R.string.settings_autohide_never_hint,
        ).forEach { id ->
            val text = ar.getString(id)
            assertTrue("resource $id is empty in Arabic", text.isNotBlank())
            // Arabic text must contain at least one Arabic letter.
            assertTrue(
                "resource $id looks untranslated: $text",
                text.any { it in '؀'..'ۿ' },
            )
        }
    }

    @Test
    fun `bubble blocker messages are localized per language`() {
        AppLocale.prime(AppLocale.ARABIC)
        val arabic = com.smsexpense.tracker.service.bubble.BubbleBlocker.NO_OVERLAY_PERMISSION
            .message(AppLocale.wrap(context))
        AppLocale.prime(AppLocale.ENGLISH)
        val english = com.smsexpense.tracker.service.bubble.BubbleBlocker.NO_OVERLAY_PERMISSION
            .message(AppLocale.wrap(context))
        assertNotEquals(arabic, english)
        assertEquals("", com.smsexpense.tracker.service.bubble.BubbleBlocker.NONE.message(context))
    }

    @Test
    fun `quick launch options are described in the selected language`() {
        // Each method is asked directly with an explicitly localized context rather
        // than through the registry, which reads the process-wide cached language.
        fun describeIn(language: String) = run {
            AppLocale.prime(language)
            val localized = AppLocale.wrap(context)
            com.smsexpense.tracker.service.quicklaunch.QuickLaunchRegistry.methods
                .map { it.describe(localized, backTapEnabled = false) }
        }

        val arabic = describeIn(AppLocale.ARABIC)
        val english = describeIn(AppLocale.ENGLISH)

        arabic.zip(english).forEach { (ar, en) ->
            assertNotEquals("${ar.id} title not translated", ar.title, en.title)
            assertNotEquals("${ar.id} description not translated", ar.description, en.description)
        }
    }

    @Test
    fun `supported languages are exactly the ones the picker offers`() {
        assertEquals(
            listOf(AppLocale.SYSTEM, AppLocale.ENGLISH, AppLocale.ARABIC),
            AppLocale.SUPPORTED,
        )
    }

    @Test
    fun `a primed language survives the app's own startup seeding`() {
        // The Application seeds the cached locale from settings. While that
        // seeding was asynchronous it landed a few hundred milliseconds into the
        // process and overwrote whatever had been primed in the meantime — which
        // reverted a language picked inside that window, and made every test that
        // primes a language racy. Seeding is synchronous now; nothing may land later.
        ApplicationProvider.getApplicationContext<Context>()

        AppLocale.prime(AppLocale.ARABIC)

        repeat(12) {
            Thread.sleep(25)
            assertEquals(
                "something overwrote the primed language after startup",
                AppLocale.ARABIC,
                AppLocale.current(),
            )
        }
    }
}
