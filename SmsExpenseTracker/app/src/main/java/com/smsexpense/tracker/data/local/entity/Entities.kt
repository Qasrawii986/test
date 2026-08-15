package com.smsexpense.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["categoryId"]),
        Index(value = ["status"]),
        Index(value = ["syncStatus"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val categoryId: Long?,
    val sender: String,
    val originalMessage: String,
    val timestamp: Long,
    val status: String,
    val syncStatus: String,
    val confidence: Float,
    val dedupKey: String,
    val createdAt: Long,
    // Added in DB v2. Default keeps rows from v1 valid (they were all realtime SMS).
    @ColumnInfo(defaultValue = "SMS_REALTIME")
    val source: String = "SMS_REALTIME",
)

@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importedAt: Long,
    val fromDate: Long,
    val toDate: Long,
    val transactionCount: Int,
    val totalAmount: Double,
    val currency: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long?,
    val sortOrder: Int,
    val createdAt: Long,
)
