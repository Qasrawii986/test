package com.smsexpense.tracker.domain.parser

import com.smsexpense.tracker.domain.model.PaymentCandidate
import java.security.MessageDigest

/**
 * Stable identity for a payment so re-delivered broadcasts / duplicate SMS never
 * create a second row. Backed by a UNIQUE index on payments.dedupKey.
 */
object DedupKey {
    fun of(candidate: PaymentCandidate): String = sha256(
        listOf(
            candidate.sender.trim().uppercase(),
            candidate.timestamp.toString(),
            candidate.originalMessage.trim(),
            candidate.amount.toString(),
        ).joinToString("|")
    )

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
