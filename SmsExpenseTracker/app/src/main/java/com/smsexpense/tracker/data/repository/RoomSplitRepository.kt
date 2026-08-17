package com.smsexpense.tracker.data.repository

import com.smsexpense.tracker.data.local.dao.AllocationDao
import com.smsexpense.tracker.data.local.dao.OwedRow
import com.smsexpense.tracker.data.local.dao.PayerDao
import com.smsexpense.tracker.data.local.entity.AllocationEntity
import com.smsexpense.tracker.data.local.entity.PayerEntity
import com.smsexpense.tracker.domain.model.Allocation
import com.smsexpense.tracker.domain.model.CategoryTotal
import com.smsexpense.tracker.domain.model.OwedTotal
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.domain.model.PaymentSplit
import com.smsexpense.tracker.domain.repository.SplitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSplitRepository(
    private val payerDao: PayerDao,
    private val allocationDao: AllocationDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SplitRepository {

    override fun observePayers(): Flow<List<Payer>> =
        payerDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getPayers(): List<Payer> = payerDao.getAll().map { it.toDomain() }

    override suspend fun selfPayerId(): Long? = payerDao.getSelf()?.id

    override suspend fun seedSelfIfEmpty() {
        if (payerDao.getSelf() != null) return
        payerDao.insert(
            PayerEntity(
                // Displayed through a localized string because this row is "you".
                name = SELF_NAME,
                emoji = "🙋",
                color = null,
                isSelf = true,
                sortOrder = 0,
                createdAt = clock(),
            )
        )
    }

    override suspend fun addPayer(name: String, emoji: String, color: Long?): Long =
        payerDao.insert(
            PayerEntity(
                name = name.trim(),
                emoji = emoji.ifBlank { "👤" },
                color = color,
                isSelf = false,
                sortOrder = payerDao.maxSortOrder() + 1,
                createdAt = clock(),
            )
        )

    override suspend fun updatePayer(payer: Payer) {
        val existing = payerDao.getById(payer.id) ?: return
        payerDao.update(
            existing.copy(
                name = payer.name.trim(),
                emoji = payer.emoji.ifBlank { "👤" },
                color = payer.color,
                sortOrder = payer.sortOrder,
                // isSelf is structural, never editable.
            )
        )
    }

    override suspend fun deletePayer(id: Long) {
        // Their allocations cascade away, which returns those payments to being
        // fully yours — the only sane outcome that keeps every split adding up.
        payerDao.deleteIfNotSelf(id)
    }

    override suspend fun chargedCount(payerId: Long): Int = allocationDao.countForPayer(payerId)

    override fun observeSplit(paymentId: Long, total: Double, currency: String): Flow<PaymentSplit> =
        allocationDao.observeForPayment(paymentId).map { rows ->
            PaymentSplit(paymentId, total, currency, rows.map { it.toDomain() })
        }

    override suspend fun getSplit(paymentId: Long, total: Double, currency: String): PaymentSplit =
        PaymentSplit(
            paymentId = paymentId,
            total = total,
            currency = currency,
            allocations = allocationDao.forPayment(paymentId).map { it.toDomain() },
        )

    override suspend fun setSplit(paymentId: Long, amountsByPayer: Map<Long, Double>) {
        val selfId = payerDao.getSelf()?.id
        val wanted = amountsByPayer
            .filterKeys { it != selfId }
            .filterValues { it > PaymentSplit.CENT }
        // Keep settled flags for payers whose charge is unchanged, so editing one
        // person's amount does not silently re-open a debt someone already paid.
        val previous = allocationDao.forPayment(paymentId)
        val settledBefore = previous
            .filter { it.settled }
            .associate { it.payerId to it.amount }
        allocationDao.deleteForPayment(paymentId)
        if (wanted.isEmpty()) return
        allocationDao.insertAll(
            wanted.map { (payerId, amount) ->
                AllocationEntity(
                    paymentId = paymentId,
                    payerId = payerId,
                    amount = amount,
                    settled = settledBefore[payerId]?.let {
                        kotlin.math.abs(it - amount) < PaymentSplit.CENT
                    } ?: false,
                )
            }
        )
    }

    override suspend fun chargeWholePayment(paymentId: Long, payerId: Long, total: Double) {
        val selfId = payerDao.getSelf()?.id
        if (payerId == selfId) {
            // Back to being entirely your own expense.
            allocationDao.deleteForPayment(paymentId)
            return
        }
        setSplit(paymentId, mapOf(payerId to total))
    }

    override fun observeOwedBetween(from: Long, to: Long): Flow<List<OwedTotal>> =
        allocationDao.observeOwedBetween(from, to).map { rows -> rows.map { it.toDomain() } }

    override fun observeOwedAllTime(): Flow<List<OwedTotal>> =
        allocationDao.observeOwedAllTime().map { rows -> rows.map { it.toDomain() } }

    override fun observeChargedToOthersBetween(from: Long, to: Long): Flow<Double> =
        allocationDao.observeChargedToOthersBetween(from, to)

    override fun observeChargedToOthersByCategoryBetween(
        from: Long,
        to: Long,
    ): Flow<List<CategoryTotal>> =
        allocationDao.observeChargedToOthersByCategoryBetween(from, to).map { rows ->
            rows.map { CategoryTotal(it.categoryId, it.total, it.count) }
        }

    override fun observeSharedPaymentIdsBetween(from: Long, to: Long): Flow<Set<Long>> =
        allocationDao.observeSharedPaymentIdsBetween(from, to).map { it.toSet() }

    override suspend fun settleAllFor(payerId: Long) = allocationDao.settleAllFor(payerId)

    override suspend fun unsettleAllFor(payerId: Long) = allocationDao.unsettleAllFor(payerId)

    override suspend fun setSettled(allocationId: Long, settled: Boolean) =
        allocationDao.setSettled(allocationId, settled)

    companion object {
        /** Stored name of the self payer; the UI shows a translated label instead. */
        const val SELF_NAME = "You"
    }
}

private fun PayerEntity.toDomain() = Payer(
    id = id,
    name = name,
    emoji = emoji,
    color = color,
    isSelf = isSelf,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

private fun AllocationEntity.toDomain() = Allocation(
    id = id,
    paymentId = paymentId,
    payerId = payerId,
    amount = amount,
    settled = settled,
)

private fun OwedRow.toDomain() = OwedTotal(payerId = payerId, amount = amount, count = count)
