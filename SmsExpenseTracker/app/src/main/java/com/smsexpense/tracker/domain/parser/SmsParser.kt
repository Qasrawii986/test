package com.smsexpense.tracker.domain.parser

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentCandidate

/** Outcome of parsing a message. Richer than a nullable so the debug screen can explain itself. */
sealed class ParseOutcome {
    data class Payment(val candidate: PaymentCandidate) : ParseOutcome()
    data class NotPayment(val reason: String, val confidence: Float) : ParseOutcome()
}

/**
 * Extracts payment data from bank messages.
 *
 * Design:
 *  - [positiveSignals] / [negativeSignals] drive a confidence score; nothing is hardcoded
 *    to a single bank format.
 *  - Amount+currency extraction runs over the digit-normalized text so Arabic-Indic
 *    digits and separators behave like ASCII.
 *  - New formats are added by appending to the signal lists or [merchantPatterns] —
 *    no receiver or service code changes needed.
 */
class SmsParser(
    private val positiveSignals: List<Signal> = DEFAULT_POSITIVE,
    private val negativeSignals: List<Signal> = DEFAULT_NEGATIVE,
) {

    data class Signal(val regex: Regex, val weight: Float, val label: String)

    fun parse(message: IncomingMessage): ParseOutcome {
        val original = message.body
        if (original.isBlank()) return ParseOutcome.NotPayment("Empty message", 0f)

        val text = AmountNormalizer.normalizeDigits(original)

        var score = 0f
        val matchedNegative = negativeSignals.filter { it.regex.containsMatchIn(text) }
        val matchedPositive = positiveSignals.filter { it.regex.containsMatchIn(text) }
        score += matchedPositive.sumOf { it.weight.toDouble() }.toFloat()
        score -= matchedNegative.sumOf { it.weight.toDouble() }.toFloat()

        val amountWithCurrency = extractAmountAndCurrency(text)
        if (amountWithCurrency != null) {
            score += 0.25f
            if (amountWithCurrency.currency != null) score += 0.15f
        }

        val confidence = score.coerceIn(0f, 1f)

        if (matchedNegative.isNotEmpty() && matchedNegative.sumOf { it.weight.toDouble() } >=
            matchedPositive.sumOf { it.weight.toDouble() }
        ) {
            return ParseOutcome.NotPayment(
                "Negative signal(s): ${matchedNegative.joinToString { it.label }}",
                confidence,
            )
        }
        if (matchedPositive.isEmpty()) {
            return ParseOutcome.NotPayment("No payment keywords found", confidence)
        }
        if (amountWithCurrency == null) {
            return ParseOutcome.NotPayment("No amount found", confidence)
        }

        val merchant = extractMerchant(original)

        return ParseOutcome.Payment(
            PaymentCandidate(
                amount = amountWithCurrency.amount,
                currency = amountWithCurrency.currency ?: DEFAULT_CURRENCY_PLACEHOLDER,
                merchant = merchant,
                sender = message.sender,
                originalMessage = original,
                timestamp = message.timestamp,
                confidence = confidence,
            )
        )
    }

    private data class AmountCurrency(val amount: Double, val currency: String?)

    private fun extractAmountAndCurrency(text: String): AmountCurrency? {
        // currency before amount: "JOD 25.00" / "بقيمة 15.750 دينار" (currency after)
        val cur = CurrencyNormalizer.aliasPattern
        val num = """\d{1,3}(?:,\d{3})+(?:\.\d{1,3})?|\d+(?:[.,]\d{1,3})?"""

        val currencyFirst = Regex("""(?i)(?<![\w\d])($cur)\s*($num)""")
        val currencyAfter = Regex("""(?i)($num)\s*($cur)(?![\w])""")

        currencyFirst.find(text)?.let { m ->
            val amount = AmountNormalizer.parseAmount(m.groupValues[2])
            val currency = CurrencyNormalizer.normalize(m.groupValues[1])
            if (amount != null) return AmountCurrency(amount, currency)
        }
        currencyAfter.find(text)?.let { m ->
            val amount = AmountNormalizer.parseAmount(m.groupValues[1])
            val currency = CurrencyNormalizer.normalize(m.groupValues[2])
            if (amount != null) return AmountCurrency(amount, currency)
        }

        // Amount with no currency, anchored to a payment word so we don't grab card digits.
        val anchored = Regex(
            """(?i)(?:بمبلغ|بقيمة|مبلغ|خصم|amount of|charged|of)\s*:?\s*($num)"""
        )
        anchored.find(text)?.let { m ->
            val amount = AmountNormalizer.parseAmount(m.groupValues[1])
            if (amount != null) return AmountCurrency(amount, null)
        }
        return null
    }

    private fun extractMerchant(original: String): String? {
        for (pattern in merchantPatterns) {
            val m = pattern.find(original) ?: continue
            val candidate = m.groupValues[1].trim().trimEnd('.', '،', ',', '؛', ';')
            if (candidate.length in 2..40) return candidate
        }
        return null
    }

    companion object {
        /** Used when a payment amount was found but no currency token; replaced by the
         *  user's default currency at ingestion time. */
        const val DEFAULT_CURRENCY_PLACEHOLDER = ""

        val DEFAULT_POSITIVE: List<Signal> = listOf(
            Signal(Regex("""تم\s+خصم"""), 0.5f, "خصم"),
            Signal(Regex("""عملية\s+شراء|تمت\s+عملية"""), 0.5f, "شراء"),
            Signal(Regex("""تم\s+استخدام\s+البطاقة"""), 0.5f, "استخدام البطاقة"),
            Signal(Regex("""(?i)purchase"""), 0.5f, "purchase"),
            Signal(Regex("""(?i)charged|debited"""), 0.5f, "charged"),
            Signal(Regex("""(?i)\bPOS\b|نقاط\s+البيع"""), 0.35f, "POS"),
            Signal(Regex("""بمبلغ|بقيمة"""), 0.2f, "amount phrase"),
            Signal(Regex("""بطاقت|(?i)\bcard\b"""), 0.15f, "card"),
            Signal(Regex("""(?i)payment\s+of|دفع"""), 0.3f, "payment"),
        )

        val DEFAULT_NEGATIVE: List<Signal> = listOf(
            Signal(Regex("""راتب|(?i)salary"""), 0.9f, "salary"),
            Signal(Regex("""تحويل|حوالة|(?i)transfer"""), 0.7f, "transfer"),
            Signal(Regex("""إيداع|ايداع|(?i)deposit"""), 0.8f, "deposit"),
            Signal(Regex("""استرداد|(?i)refund|reversal"""), 0.8f, "refund"),
            Signal(Regex("""رمز\s+التحقق|كلمة\s+المرور|(?i)\bOTP\b|verification\s+code|one[- ]time"""), 1.0f, "OTP"),
            Signal(Regex("""رصيدك\s+الحالي\s+هو|(?i)your\s+balance\s+is"""), 0.5f, "balance info"),
        )

        val merchantPatterns: List<Regex> = listOf(
            Regex("""(?:لدى|عند)\s+(.{2,40}?)(?:\s+(?:بتاريخ|في\s+\d|بمبلغ|بقيمة)|[.،,؛\n]|$)"""),
            Regex("""(?i)\bat\s+([A-Za-z0-9&'\-. ]{2,40}?)(?:\s+on\s+|\s+using\s+|[.,\n]|$)"""),
            Regex("""(?i)\bfrom\s+merchant\s+(.{2,40}?)(?:[.,\n]|$)"""),
            Regex("""من\s+متجر\s+(.{2,40}?)(?:[.،,\n]|$)"""),
        )
    }
}
