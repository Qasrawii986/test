package com.smsexpense.tracker.domain.parser

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.model.TransactionType
import com.smsexpense.tracker.domain.model.isExpense

/** Outcome of parsing a message. Richer than a nullable so the debug screen can explain itself. */
sealed class ParseOutcome {
    data class Payment(val candidate: PaymentCandidate) : ParseOutcome()
    data class NotPayment(
        val reason: String,
        val confidence: Float,
        val type: TransactionType = TransactionType.UNKNOWN,
    ) : ParseOutcome()
}

/**
 * Classifies bank messages by MEANING, in priority order:
 *
 *  1. OTP / authorization → never a payment, even with an amount (hard stop).
 *  2. Incoming transfer (CliQ or otherwise) → not an expense (hard stop).
 *  3. Balance-only notification → not a transaction.
 *  4. Scored positive/negative signals decide completed outgoing transactions;
 *     the first matching typed signal fixes the [TransactionType]
 *     (card purchase, outgoing CliQ, bank transfer out, cash withdrawal).
 *
 * "Available balance: X" segments are stripped BEFORE amount extraction so a
 * balance can never be mistaken for the transaction amount.
 *
 * New formats are added by appending to the signal/pattern lists — no receiver,
 * service, or import code changes needed.
 */
class SmsParser(
    private val positiveSignals: List<Signal> = DEFAULT_POSITIVE,
    private val negativeSignals: List<Signal> = DEFAULT_NEGATIVE,
) {

    data class Signal(
        val regex: Regex,
        val weight: Float,
        val label: String,
        val type: TransactionType? = null,
    )

    fun parse(message: IncomingMessage): ParseOutcome {
        val original = message.body
        if (original.isBlank()) {
            return ParseOutcome.NotPayment("Empty message", 0f, TransactionType.NON_TRANSACTION)
        }

        val text = AmountNormalizer.normalizeDigits(original)

        // --- Priority 1: OTP / authorization codes. An OTP quoting an amount is
        // NOT proof the transaction completed — never import it.
        if (OTP_PATTERNS.any { it.containsMatchIn(text) }) {
            return ParseOutcome.NotPayment("OTP / authorization message", 0f, TransactionType.OTP)
        }

        // --- Priority 2: incoming money. Detected and named, but never an expense.
        if (INCOMING_PATTERNS.any { it.containsMatchIn(text) }) {
            return ParseOutcome.NotPayment(
                "Incoming transfer (money received, not an expense)",
                0f,
                TransactionType.INCOMING_TRANSFER,
            )
        }

        // --- Strip balance segments so "Available balance: 594.511 JOD" can never
        // be picked up as the transaction amount.
        val textForAmount = BALANCE_PATTERN.replace(text, " ")
        val hadBalance = textForAmount != text

        var score = 0f
        val matchedNegative = negativeSignals.filter { it.regex.containsMatchIn(text) }
        val matchedPositive = positiveSignals.filter { it.regex.containsMatchIn(text) }
        score += matchedPositive.sumOf { it.weight.toDouble() }.toFloat()
        score -= matchedNegative.sumOf { it.weight.toDouble() }.toFloat()

        val amountWithCurrency = extractAmountAndCurrency(textForAmount)
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
                TransactionType.NON_TRANSACTION,
            )
        }
        if (matchedPositive.isEmpty()) {
            return if (hadBalance) {
                ParseOutcome.NotPayment("Balance notification", confidence, TransactionType.BALANCE_UPDATE)
            } else {
                ParseOutcome.NotPayment("No payment keywords found", confidence)
            }
        }
        if (amountWithCurrency == null) {
            return ParseOutcome.NotPayment("No amount found", confidence)
        }

        // First matching typed signal (list order = priority) decides the type.
        val type = matchedPositive.firstNotNullOfOrNull { it.type } ?: TransactionType.CARD_PURCHASE
        check(type.isExpense) { "Positive signals must map to expense types" }

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
                type = type,
            )
        )
    }

    private data class AmountCurrency(val amount: Double, val currency: String?)

    private fun extractAmountAndCurrency(text: String): AmountCurrency? {
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

        /** Hard-stop authorization/OTP indicators — checked before anything else. */
        val OTP_PATTERNS: List<Regex> = listOf(
            Regex("""(?i)\bOTP\b"""),
            Regex("""رمز\s+التأكيد|رمز\s+التحقق|رمز\s+المرور|كلمة\s+المرور\s+لمرة"""),
            Regex("""(?i)verification\s+code|one[- ]time\s+pass(?:word|code)|auth(?:orization)?\s+code"""),
            Regex("""لا\s+تشارك|لا\s+تعطي\s+الرمز|(?i)do\s+not\s+share"""),
        )

        /** Money coming IN — detected, named, never an expense. */
        val INCOMING_PATTERNS: List<Regex> = listOf(
            Regex("""(?i)(?:cliq\s+)?transfer\s+(?:received\s+)?from"""),
            Regex("""تحويل\s+من|حوالة\s+من|حوالة\s+واردة|وصلتك?\s+حوالة"""),
            Regex("""إلى\s+حسابك|الى\s+حسابك|(?i)credited\s+to\s+your|received\s+in\s+your"""),
            Regex("""(?i)عبر\s+(?:cliq|كليك)\s+من"""),
        )

        /** "Available balance: X" style segments, removed before amount extraction. */
        val BALANCE_PATTERN: Regex = Regex(
            """(?i)(?:available\s+balance|\bbalance\b|الرصيد\s+المتاح|الرصيد\s+الحالي|رصيدك\s+المتاح)\s*:?\s*(?:${CurrencyNormalizer.aliasPattern})?\s*[\d][\d.,]*\s*(?:${CurrencyNormalizer.aliasPattern})?"""
        )

        val DEFAULT_POSITIVE: List<Signal> = listOf(
            // Outgoing CliQ transfer — any casing, English or Arabic, recipient after to/إلى.
            Signal(
                Regex("""(?i)(?:cliq|كليك)[^\n]*?(?:transfer(?:red)?\s+to|إلى|الى)"""),
                0.6f, "CliQ out", TransactionType.CLIQ_TRANSFER_OUT,
            ),
            Signal(
                Regex("""من\s+حسابك\s+إلى|من\s+حسابك\s+الى|(?i)transferred\s+from\s+your\s+account"""),
                0.5f, "bank transfer out", TransactionType.BANK_TRANSFER_OUT,
            ),
            Signal(
                Regex("""سحب\s+نقدي|عملية\s+سحب|(?i)cash\s+withdrawal|\bATM\s+withdrawal"""),
                0.5f, "cash withdrawal", TransactionType.CASH_WITHDRAWAL,
            ),
            Signal(Regex("""تم\s+خصم"""), 0.5f, "خصم", TransactionType.CARD_PURCHASE),
            Signal(Regex("""عملية\s+شراء|تمت\s+عملية"""), 0.5f, "شراء", TransactionType.CARD_PURCHASE),
            Signal(Regex("""تم\s+استخدام\s+البطاقة"""), 0.5f, "استخدام البطاقة", TransactionType.CARD_PURCHASE),
            Signal(Regex("""(?i)purchase"""), 0.5f, "purchase", TransactionType.CARD_PURCHASE),
            Signal(Regex("""(?i)charged|debited"""), 0.5f, "charged", TransactionType.CARD_PURCHASE),
            Signal(Regex("""(?i)\bPOS\b|نقاط\s+البيع"""), 0.35f, "POS", TransactionType.CARD_PURCHASE),
            Signal(Regex("""بمبلغ|بقيمة"""), 0.2f, "amount phrase"),
            Signal(Regex("""بطاقت|(?i)\bcard\b"""), 0.15f, "card"),
            Signal(Regex("""(?i)payment\s+of|دفع"""), 0.3f, "payment"),
        )

        val DEFAULT_NEGATIVE: List<Signal> = listOf(
            Signal(Regex("""راتب|(?i)salary"""), 0.9f, "salary"),
            Signal(Regex("""إيداع|ايداع|(?i)deposit"""), 0.8f, "deposit"),
            Signal(Regex("""استرداد|(?i)refund|reversal"""), 0.8f, "refund"),
            Signal(Regex("""رصيدك\s+الحالي\s+هو|(?i)your\s+balance\s+is"""), 0.5f, "balance info"),
            // NOTE: a blanket "transfer/تحويل" negative used to live here and made the
            // parser blind to outgoing CliQ transfers. Incoming transfers are now
            // handled directionally by INCOMING_PATTERNS instead.
            Signal(Regex("""(?i)declined|فشلت\s+العملية|تم\s+رفض"""), 0.9f, "declined"),
        )

        val merchantPatterns: List<Regex> = listOf(
            // CliQ recipient: "CliQ transfer to Abdulraheem Rizk." / "عبر كليك إلى فلان"
            Regex("""(?i)(?:cliq|كليك)[^\n]*?(?:transfer(?:red)?\s+to|إلى|الى)\s+([^.,،؛\n]{2,40}?)(?:\s*[.,،؛\n]|$)"""),
            Regex("""(?:لدى|عند)\s+(.{2,40}?)(?:\s+(?:بتاريخ|في\s+\d|بمبلغ|بقيمة)|[.،,؛\n]|$)"""),
            Regex("""(?i)\bat\s+([A-Za-z0-9&'\-. ]{2,40}?)(?:\s+on\s+|\s+using\s+|[.,\n]|$)"""),
            Regex("""(?i)\bfrom\s+merchant\s+(.{2,40}?)(?:[.,\n]|$)"""),
            Regex("""من\s+متجر\s+(.{2,40}?)(?:[.،,\n]|$)"""),
        )
    }
}
