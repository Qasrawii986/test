package com.smsexpense.tracker.domain.model

/**
 * A raw message entering the pipeline. The domain layer never knows whether it
 * came from SMS, a notification listener, or the debug screen.
 */
data class IncomingMessage(
    val sender: String,
    val body: String,
    val timestamp: Long,
)

/**
 * A parsed payment extracted from an [IncomingMessage], before persistence.
 */
data class PaymentCandidate(
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val sender: String,
    val originalMessage: String,
    val timestamp: Long,
    val confidence: Float,
)

enum class PaymentStatus { UNCATEGORIZED, CATEGORIZED }

enum class SyncStatus { PENDING, SYNCED, FAILED, DISABLED }

/** Where a payment entered the pipeline. */
enum class PaymentSource { SMS_REALTIME, SMS_HISTORICAL, NOTIFICATION }

data class Payment(
    val id: Long,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val categoryId: Long?,
    val sender: String,
    val originalMessage: String,
    val timestamp: Long,
    val status: PaymentStatus,
    val syncStatus: SyncStatus,
    val confidence: Float,
    val createdAt: Long,
    val source: PaymentSource = PaymentSource.SMS_REALTIME,
)

/** One row in the import history log. */
data class ImportRecord(
    val id: Long,
    val importedAt: Long,
    val fromDate: Long,
    val toDate: Long,
    val transactionCount: Int,
    val totalAmount: Double,
    val currency: String,
)

data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long?,
    val sortOrder: Int,
    val createdAt: Long,
)

data class CategoryTotal(
    val categoryId: Long?,
    val total: Double,
    val count: Int,
)

data class MonthlyStats(
    val year: Int,
    val month: Int, // 1..12
    val total: Double,
    val transactionCount: Int,
    val perCategory: List<CategoryTotal>,
)
