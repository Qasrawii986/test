package com.smsexpense.tracker.data.repository

import com.smsexpense.tracker.data.local.dao.ImportHistoryDao
import com.smsexpense.tracker.data.local.entity.ImportHistoryEntity
import com.smsexpense.tracker.domain.model.ImportRecord
import com.smsexpense.tracker.domain.repository.ImportHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomImportHistoryRepository(
    private val dao: ImportHistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ImportHistoryRepository {

    override suspend fun record(fromDate: Long, toDate: Long, count: Int, total: Double, currency: String) {
        dao.insert(
            ImportHistoryEntity(
                importedAt = clock(),
                fromDate = fromDate,
                toDate = toDate,
                transactionCount = count,
                totalAmount = total,
                currency = currency,
            )
        )
    }

    override fun observeAll(): Flow<List<ImportRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
}
