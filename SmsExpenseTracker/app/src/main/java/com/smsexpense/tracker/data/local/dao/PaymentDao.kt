package com.smsexpense.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

data class CategoryTotalRow(
    val categoryId: Long?,
    val total: Double,
    val count: Int,
)

@Dao
interface PaymentDao {

    /** Returns -1 when the row already exists (same dedupKey) — the dedup mechanism. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: Long): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = 'UNCATEGORIZED' ORDER BY timestamp DESC")
    fun observeUncategorized(): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE timestamp >= :from AND timestamp < :to")
    fun observeTotalBetween(from: Long, to: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM payments WHERE timestamp >= :from AND timestamp < :to")
    fun observeCountBetween(from: Long, to: Long): Flow<Int>

    @Query(
        """SELECT categoryId, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS count
           FROM payments WHERE timestamp >= :from AND timestamp < :to
           GROUP BY categoryId ORDER BY total DESC"""
    )
    fun observeCategoryTotalsBetween(from: Long, to: Long): Flow<List<CategoryTotalRow>>

    @Query("UPDATE payments SET categoryId = :categoryId, status = 'CATEGORIZED', syncStatus = :syncStatus WHERE id = :id")
    suspend fun categorize(id: Long, categoryId: Long, syncStatus: String)

    @Query("UPDATE payments SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryRefs(categoryId: Long)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM payments WHERE syncStatus IN ('PENDING','FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSync(): List<PaymentEntity>

    @Query("UPDATE payments SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("DELETE FROM payments")
    suspend fun clearAll()

    /** Which of these dedup keys already exist. Callers chunk the list (SQLite variable limit). */
    @Query("SELECT dedupKey FROM payments WHERE dedupKey IN (:keys)")
    suspend fun existingDedupKeys(keys: List<String>): List<String>

    /**
     * Cross-source duplicate guard: a realtime-captured SMS carries the SMSC timestamp
     * while the same message read back from the device inbox carries the received-at
     * timestamp, so the exact dedupKey can differ. Same sender+body+amount within a
     * small window is the same payment.
     */
    @Query(
        """SELECT COUNT(*) FROM payments
           WHERE sender = :sender COLLATE NOCASE AND originalMessage = :message
             AND amount = :amount AND ABS(timestamp - :timestamp) <= :windowMs"""
    )
    suspend fun countSimilar(sender: String, message: String, amount: Double, timestamp: Long, windowMs: Long): Int

    /**
     * The same purchase seen through a different channel: a tap payment posts a
     * wallet notification and the bank then texts about it. Texts differ, so only
     * amount and closeness in time can link them. Restricted to a *different*
     * source so two genuine same-amount purchases from one channel still count twice.
     */
    @Query(
        """SELECT COUNT(*) FROM payments
           WHERE amount = :amount AND ABS(timestamp - :timestamp) <= :windowMs
             AND source <> :source"""
    )
    suspend fun countCrossSourceTwins(amount: Double, timestamp: Long, windowMs: Long, source: String): Int
}
