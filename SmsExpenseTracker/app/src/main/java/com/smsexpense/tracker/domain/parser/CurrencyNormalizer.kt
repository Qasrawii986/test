package com.smsexpense.tracker.domain.parser

/**
 * Maps the many ways a currency appears in bank SMS text to an ISO-like code.
 */
object CurrencyNormalizer {

    private val aliases: Map<String, String> = buildMap {
        put("JOD", "JOD"); put("JD", "JOD"); put("دينار", "JOD"); put("د.ا", "JOD")
        put("دينار اردني", "JOD"); put("دينار أردني", "JOD")
        put("USD", "USD"); put("$", "USD"); put("دولار", "USD")
        put("SAR", "SAR"); put("ريال", "SAR"); put("ر.س", "SAR"); put("SR", "SAR")
        put("EUR", "EUR"); put("€", "EUR"); put("يورو", "EUR")
        put("AED", "AED"); put("درهم", "AED"); put("د.إ", "AED")
        put("EGP", "EGP"); put("جنيه", "EGP")
        put("ILS", "ILS"); put("شيكل", "ILS"); put("شيقل", "ILS"); put("NIS", "ILS")
        put("KWD", "KWD"); put("QAR", "QAR"); put("BHD", "BHD"); put("GBP", "GBP")
    }

    /** Regex alternation of every alias, longest first so "دينار اردني" wins over "دينار". */
    val aliasPattern: String = aliases.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    fun normalize(raw: String): String? = aliases[raw.trim().uppercase()] ?: aliases[raw.trim()]
}
