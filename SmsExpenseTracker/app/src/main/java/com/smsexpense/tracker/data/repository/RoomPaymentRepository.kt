package com.smsexpense.tracker.data.repository

import com.smsexpense.tracker.data.local.dao.PaymentDao
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.domain.model.CategoryTotal
import com.smsexpense.tracker.domain.model.MonthlyStats
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.domain.model.SyncStatus
import com.smsexpense.tracker.domain.parser.DedupKey
import com.smsexpense.tracker.domain.repository.IngestResult
import com.smsexpense.tracker.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class RoomPaymentRepository(
    private val paymentDao: PaymentDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PaymentRepository {

    override suspend fun ingest(
        candidate: PaymentCandidate,
        source: PaymentSource,
        categoryId: Long?,
    ): IngestResult {
        val entity = PaymentEntity(
            amount = candidate.amount,
            currency = candidate.currency,
            merchant = candidate.merchant,
            categoryId = categoryId,
            sender = candidate.sender,
            originalMessage = candidate.originalMessage,
            timestamp = candidate.timestamp,
            status = (if (categoryId != null) PaymentStatus.CATEGORIZED else PaymentStatus.UNCATEGORIZED).name,
            syncStatus = SyncStatus.PENDING.name,
            confidence = candidate.confidence,
            dedupKey = DedupKey.of(candidate),
            createdAt = clock(),
            source = source.name,
        )
        val rowId = paymentDao.insertIgnoring(entity)
        return if (rowId == -1L) IngestResult.Duplicate else IngestResult.Inserted(rowId)
    }

    override suspend fun existingDedupKeys(keys: Collection<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        // SQLite caps bound variables (999 historically); chunk to stay safe.
        return keys.toList().chunked(500)
            .flatMap { chunk -> paymentDao.existingDedupKeys(chunk) }
            .toSet()
    }

    override suspend fun hasSimilar(
        sender: String,
        message: String,
        amount: Double,
        timestamp: Long,
        windowMs: Long,
    ): Boolean = paymentDao.countSimilar(sender, message, amount, timestamp, windowMs) > 0

    override suspend fun getById(id: Long): Payment? = paymentDao.getById(id)?.toDomain()

    override fun observeById(id: Long): Flow<Payment?> =
        paymentDao.observeById(id).map { it?.toDomain() }

    override fun observeMonth(year: Int, month: Int): Flow<List<Payment>> {
        val (from, to) = monthBounds(year, month)
        return paymentDao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }
    }

    override fun observeUncategorized(): Flow<List<Payment>> =
        paymentDao.observeUncategorized().map { list -> list.map { it.toDomain() } }

    override fun observeMonthlyStats(year: Int, month: Int): Flow<MonthlyStats> {
        val (from, to) = monthBounds(year, month)
        return combine(
            paymentDao.observeTotalBetween(from, to),
            paymentDao.observeCountBetween(from, to),
            paymentDao.observeCategoryTotalsBetween(from, to),
        ) { total, count, perCategory ->
            MonthlyStats(
                year = year,
                month = month,
                total = total,
                transactionCount = count,
                perCategory = perCategory.map { CategoryTotal(it.categoryId, it.total, it.count) },
            )
        }
    }

    override suspend fun categorize(paymentId: Long, categoryId: Long) {
        paymentDao.categorize(paymentId, categoryId, SyncStatus.PENDING.name)
    }

    override suspend fun delete(paymentId: Long) = paymentDao.delete(paymentId)

    override suspend fun pendingSync(): List<Payment> =
        paymentDao.pendingSync().map { it.toDomain() }

    override suspend fun markSync(paymentId: Long, status: SyncStatus) {
        paymentDao.updateSyncStatus(paymentId, status.name)
    }

    override suspend fun clearAll() = paymentDao.clearAll()

    companion object {
        fun monthBounds(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
            val start = LocalDate.of(year, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return start to end
        }
    }
}
