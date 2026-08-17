package com.smsexpense.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsexpense.tracker.data.local.entity.AllocationEntity
import kotlinx.coroutines.flow.Flow

data class OwedRow(
    val payerId: Long,
    val amount: Double,
    val count: Int,
)

@Dao
interface AllocationDao {

    @Insert
    suspend fun insert(allocation: AllocationEntity): Long

    @Insert
    suspend fun insertAll(allocations: List<AllocationEntity>)

    @Query("SELECT * FROM allocations WHERE paymentId = :paymentId")
    suspend fun forPayment(paymentId: Long): List<AllocationEntity>

    @Query("SELECT * FROM allocations WHERE paymentId = :paymentId")
    fun observeForPayment(paymentId: Long): Flow<List<AllocationEntity>>

    @Query("DELETE FROM allocations WHERE paymentId = :paymentId")
    suspend fun deleteForPayment(paymentId: Long)

    @Query("DELETE FROM allocations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE allocations SET settled = :settled WHERE id = :id")
    suspend fun setSettled(id: Long, settled: Boolean)

    /** "He paid me back": clears every outstanding charge for one payer. */
    @Query("UPDATE allocations SET settled = 1 WHERE payerId = :payerId AND settled = 0")
    suspend fun settleAllFor(payerId: Long)

    @Query("UPDATE allocations SET settled = 0 WHERE payerId = :payerId")
    suspend fun unsettleAllFor(payerId: Long)

    /**
     * How much of this period's spending was charged to other people. Your own
     * share is the period total minus this — see [com.smsexpense.tracker.domain.model.PaymentSplit].
     */
    @Query(
        """SELECT COALESCE(SUM(a.amount), 0) FROM allocations a
           INNER JOIN payments p ON p.id = a.paymentId
           WHERE p.timestamp >= :from AND p.timestamp < :to"""
    )
    fun observeChargedToOthersBetween(from: Long, to: Long): Flow<Double>

    /** Same, split by the payment's category, so reports can show your share per category. */
    @Query(
        """SELECT p.categoryId AS categoryId, COALESCE(SUM(a.amount), 0) AS total,
                  COUNT(*) AS count
           FROM allocations a
           INNER JOIN payments p ON p.id = a.paymentId
           WHERE p.timestamp >= :from AND p.timestamp < :to
           GROUP BY p.categoryId"""
    )
    fun observeChargedToOthersByCategoryBetween(from: Long, to: Long): Flow<List<CategoryTotalRow>>

    /** Outstanding per payer within a period (dashboard). */
    @Query(
        """SELECT a.payerId AS payerId, COALESCE(SUM(a.amount), 0) AS amount, COUNT(*) AS count
           FROM allocations a
           INNER JOIN payments p ON p.id = a.paymentId
           WHERE a.settled = 0 AND p.timestamp >= :from AND p.timestamp < :to
           GROUP BY a.payerId"""
    )
    fun observeOwedBetween(from: Long, to: Long): Flow<List<OwedRow>>

    /** Outstanding per payer, all time — a debt does not reset at month end. */
    @Query(
        """SELECT payerId AS payerId, COALESCE(SUM(amount), 0) AS amount, COUNT(*) AS count
           FROM allocations WHERE settled = 0 GROUP BY payerId"""
    )
    fun observeOwedAllTime(): Flow<List<OwedRow>>

    @Query("SELECT COUNT(*) FROM allocations WHERE payerId = :payerId")
    suspend fun countForPayer(payerId: Long): Int

    /** Which payments in this period are shared, so lists can badge them. */
    @Query(
        """SELECT DISTINCT a.paymentId FROM allocations a
           INNER JOIN payments p ON p.id = a.paymentId
           WHERE p.timestamp >= :from AND p.timestamp < :to"""
    )
    fun observeSharedPaymentIdsBetween(from: Long, to: Long): Flow<List<Long>>
}
