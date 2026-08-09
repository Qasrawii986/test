package com.smsexpense.tracker.domain.parser

/**
 * Normalizes numeric text found in bank messages into a Double.
 *
 * Handles:
 *  - Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) and Extended Arabic-Indic (۰..۹)
 *  - Arabic decimal separator (٫ U+066B) and Arabic thousands separator (٬ U+066C)
 *  - Western thousands separators: "1,234.56" and "1.234,56"
 *  - Plain forms: "12.50", "15.750", "32"
 */
object AmountNormalizer {

    fun normalizeDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '٠'..'٩' -> ('0' + (ch - '٠')) // Arabic-Indic
                    in '۰'..'۹' -> ('0' + (ch - '۰')) // Extended Arabic-Indic
                    '٫' -> '.' // Arabic decimal separator
                    '٬' -> ',' // Arabic thousands separator
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /**
     * Parses a raw amount token like "1,245.50", "1.234,56", "15.750" or "٣٢٫٥".
     * Returns null when the token cannot be interpreted as a positive amount.
     */
    fun parseAmount(raw: String): Double? {
        var s = normalizeDigits(raw.trim())
        if (s.isEmpty()) return null
        s = s.replace(" ", "").replace(" ", "")

        val hasComma = s.contains(',')
        val hasDot = s.contains('.')

        val normalized = when {
            hasComma && hasDot -> {
                // The right-most separator is the decimal one.
                if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                    s.replace(".", "").replace(',', '.')
                } else {
                    s.replace(",", "")
                }
            }
            hasComma -> {
                // "1,234" (thousands) vs "12,50" (decimal comma).
                val afterComma = s.substringAfterLast(',')
                val groups = s.split(',')
                val looksLikeThousands =
                    groups.size > 1 && groups.drop(1).all { it.length == 3 } && groups[0].length in 1..3
                if (looksLikeThousands && afterComma.length == 3) s.replace(",", "")
                else s.replace(',', '.')
            }
            else -> s
        }

        val value = normalized.toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }
}
