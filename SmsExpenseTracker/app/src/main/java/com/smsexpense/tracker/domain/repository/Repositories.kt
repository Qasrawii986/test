package com.smsexpense.tracker.domain.repository

import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.MonthlyStats
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentCandidate
import kotlinx.coroutines.flow.Flow

sealed class IngestResult {
    data class Inserted(val paymentId: Long) : IngestResult()
    data object Duplicate : IngestResult()
}

interface PaymentRepository {
    /** Idempotent: the same candidate (same dedup key) never creates a second row. */
    suspend fun ingest(candidate: PaymentCandidate): IngestResult
    suspend fun getById(id: Long): Payment?
    fun observeById(id: Long): Flow<Payment?>
    fun observeMonth(year: Int, month: Int): Flow<List<Payment>>
    fun observeUncategorized(): Flow<List<Payment>>
    fun observeMonthlyStats(year: Int, month: Int): Flow<MonthlyStats>
    suspend fun categorize(paymentId: Long, categoryId: Long)
    suspend fun delete(paymentId: Long)
    suspend fun pendingSync(): List<Payment>
    suspend fun markSync(paymentId: Long, status: com.smsexpense.tracker.domain.model.SyncStatus)
    suspend fun clearAll()
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun getById(id: Long): Category?
    suspend fun add(name: String, icon: String, color: Long?): Long
    suspend fun update(category: Category)
    suspend fun delete(id: Long)
    suspend fun move(id: Long, up: Boolean)
    suspend fun seedDefaultsIfEmpty()
}

data class BubbleSettings(
    val enabled: Boolean,
    val autoHideSeconds: Int,
    val startY: Int,
)

data class ApiSettings(
    val enabled: Boolean,
    val baseUrl: String,
    val authToken: String,
)

interface SettingsRepository {
    val senderIds: Flow<Set<String>>
    val defaultCurrency: Flow<String>
    val confidenceThreshold: Flow<Float>
    val bubbleSettings: Flow<BubbleSettings>
    val apiSettings: Flow<ApiSettings>

    suspend fun addSenderId(id: String)
    suspend fun removeSenderId(id: String)
    suspend fun setDefaultCurrency(code: String)
    suspend fun setConfidenceThreshold(value: Float)
    suspend fun setBubbleEnabled(enabled: Boolean)
    suspend fun setBubbleAutoHideSeconds(seconds: Int)
    suspend fun setBubbleStartY(y: Int)
    suspend fun setApiEnabled(enabled: Boolean)
    suspend fun setApiBaseUrl(url: String)
    suspend fun setApiAuthToken(token: String)
}
