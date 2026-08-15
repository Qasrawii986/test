package com.smsexpense.tracker.data.remote.dto

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PaymentDto(
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val category: String?,
    val timestamp: Long,
    val sender: String,
    val originalMessage: String,
) {
    fun toJson(): String = JSONObject().apply {
        put("amount", amount)
        put("currency", currency)
        put("merchant", merchant ?: JSONObject.NULL)
        put("category", category ?: JSONObject.NULL)
        put(
            "timestamp",
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
            ),
        )
        put("sender", sender)
        put("originalMessage", originalMessage)
    }.toString()
}
