package com.smsexpense.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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

@Entity(tableName = "payers")
data class PayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long?,
    /** The one payer representing the app's owner. Exactly one row has this set. */
    val isSelf: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "allocations",
    indices = [
        Index(value = ["paymentId"]),
        Index(value = ["payerId"]),
        Index(value = ["settled"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val payerId: Long,
    val amount: Double,
    /** Reimbursed / squared up. Only meaningful for payers other than yourself. */
    val settled: Boolean = false,
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

@Entity(
    tableName = "categories",
    indices = [Index(value = ["parentId"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    // Added in DB v3. Null = root category; existing rows stay roots.
    val parentId: Long? = null,
)
