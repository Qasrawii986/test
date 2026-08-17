package com.smsexpense.tracker.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app language. Android 13 has a platform API for this, but minSdk is 26, so
 * the app wraps its own contexts with the chosen locale instead — one mechanism
 * that behaves identically on every supported version, and one that also covers
 * the overlay windows drawn by services (which the platform API does not reach).
 *
 * The value is cached in memory because [Context.attachBaseContext] runs before
 * anything can suspend, and a language preference must be applied there.
 */
object AppLocale {

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val ARABIC = "ar"

    val SUPPORTED = listOf(SYSTEM, ENGLISH, ARABIC)

    @Volatile
    private var cached: String = SYSTEM

    /** Seeded once at app start from persisted settings. */
    fun prime(language: String) {
        cached = language
    }

    fun current(): String = cached

    /**
     * Arabic with Latin numerals. Amounts are formatted with Western digits
     * throughout the app, and Jordanian banking uses them too, so letting
     * resource strings render Arabic-Indic digits would mix "٧ عملية" with
     * "1,245.50 JOD" on the same screen.
     */
    private const val ARABIC_TAG = "ar-u-nu-latn"

    /** Returns a context whose resources resolve in the selected language. */
    fun wrap(context: Context): Context {
        val language = cached
        if (language == SYSTEM) return context
        val locale = Locale.forLanguageTag(if (language == ARABIC) ARABIC_TAG else language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    fun isRtl(): Boolean = cached == ARABIC
}

/** Convenience for services that build their own Compose views. */
fun Context.localized(): Context = AppLocale.wrap(this)
