package com.smsexpense.tracker.fakes

import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.CategoryTotal
import com.smsexpense.tracker.domain.model.MonthlyStats
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.domain.model.SyncStatus
import com.smsexpense.tracker.domain.parser.DedupKey
import com.smsexpense.tracker.domain.repository.ApiSettings
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.IngestResult
import com.smsexpense.tracker.domain.repository.PaymentRepository
import com.smsexpense.tracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePaymentRepository : PaymentRepository {
    private val payments = MutableStateFlow<List<Payment>>(emptyList())
    private val keys = mutableSetOf<String>()
    private var nextId = 1L

    fun all(): List<Payment> = payments.value

    override suspend fun ingest(
        candidate: PaymentCandidate,
        source: PaymentSource,
        categoryId: Long?,
    ): IngestResult {
        val key = DedupKey.of(candidate)
        if (!keys.add(key)) return IngestResult.Duplicate
        val payment = Payment(
            id = nextId++,
            amount = candidate.amount,
            currency = candidate.currency,
            merchant = candidate.merchant,
            categoryId = categoryId,
            sender = candidate.sender,
            originalMessage = candidate.originalMessage,
            timestamp = candidate.timestamp,
            status = if (categoryId != null) PaymentStatus.CATEGORIZED else PaymentStatus.UNCATEGORIZED,
            syncStatus = SyncStatus.PENDING,
            confidence = candidate.confidence,
            createdAt = candidate.timestamp,
            source = source,
        )
        payments.value = payments.value + payment
        return IngestResult.Inserted(payment.id)
    }

    override suspend fun existingDedupKeys(keys: Collection<String>): Set<String> =
        keys.filter { it in this.keys }.toSet()

    override suspend fun hasSimilar(
        sender: String,
        message: String,
        amount: Double,
        timestamp: Long,
        windowMs: Long,
    ): Boolean = payments.value.any {
        it.sender.equals(sender, ignoreCase = true) &&
            it.originalMessage == message &&
            it.amount == amount &&
            kotlin.math.abs(it.timestamp - timestamp) <= windowMs
    }

    override suspend fun getById(id: Long): Payment? = payments.value.find { it.id == id }

    override fun observeById(id: Long): Flow<Payment?> = payments.map { list -> list.find { it.id == id } }

    override fun observeMonth(year: Int, month: Int): Flow<List<Payment>> = payments

    override fun observeUncategorized(): Flow<List<Payment>> =
        payments.map { list -> list.filter { it.status == PaymentStatus.UNCATEGORIZED } }

    override fun observeMonthlyStats(year: Int, month: Int): Flow<MonthlyStats> = payments.map { list ->
        MonthlyStats(
            year = year,
            month = month,
            total = list.sumOf { it.amount },
            transactionCount = list.size,
            perCategory = list.groupBy { it.categoryId }.map { (categoryId, group) ->
                CategoryTotal(categoryId, group.sumOf { it.amount }, group.size)
            },
        )
    }

    override suspend fun categorize(paymentId: Long, categoryId: Long) {
        payments.value = payments.value.map {
            if (it.id == paymentId) it.copy(
                categoryId = categoryId,
                status = PaymentStatus.CATEGORIZED,
                syncStatus = SyncStatus.PENDING,
            ) else it
        }
    }

    override suspend fun delete(paymentId: Long) {
        payments.value = payments.value.filterNot { it.id == paymentId }
    }

    override suspend fun pendingSync(): List<Payment> =
        payments.value.filter { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.FAILED }

    override suspend fun markSync(paymentId: Long, status: SyncStatus) {
        payments.value = payments.value.map {
            if (it.id == paymentId) it.copy(syncStatus = status) else it
        }
    }

    override suspend fun clearAll() {
        payments.value = emptyList()
        keys.clear()
    }
}

class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Category>> = categories
    override suspend fun getAll(): List<Category> = categories.value
    override suspend fun getById(id: Long): Category? = categories.value.find { it.id == id }

    override suspend fun add(name: String, icon: String, color: Long?): Long {
        val category = Category(
            id = nextId++,
            name = name,
            icon = icon,
            color = color,
            sortOrder = categories.value.size,
            createdAt = 0L,
        )
        categories.value = categories.value + category
        return category.id
    }

    override suspend fun update(category: Category) {
        categories.value = categories.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: Long) {
        categories.value = categories.value.filterNot { it.id == id }
    }

    override suspend fun move(id: Long, up: Boolean) = Unit

    override suspend fun seedDefaultsIfEmpty() {
        if (categories.value.isEmpty()) add("Other", "📦", null)
    }
}

class FakeSettingsRepository(
    senderIdsInitial: Set<String> = setOf("MYBANK"),
    thresholdInitial: Float = 0.5f,
    currencyInitial: String = "JOD",
) : SettingsRepository {
    private val _senderIds = MutableStateFlow(senderIdsInitial)
    private val _currency = MutableStateFlow(currencyInitial)
    private val _threshold = MutableStateFlow(thresholdInitial)
    private val _bubble = MutableStateFlow(BubbleSettings(enabled = true, autoHideSeconds = 45, startY = 300))
    private val _api = MutableStateFlow(ApiSettings(enabled = false, baseUrl = "", authToken = ""))
    private val _setupCompleted = MutableStateFlow(false)

    override val senderIds: Flow<Set<String>> = _senderIds
    override val defaultCurrency: Flow<String> = _currency
    override val confidenceThreshold: Flow<Float> = _threshold
    override val bubbleSettings: Flow<BubbleSettings> = _bubble
    override val apiSettings: Flow<ApiSettings> = _api
    override val setupCompleted: Flow<Boolean> = _setupCompleted

    override suspend fun addSenderId(id: String) { _senderIds.value = _senderIds.value + id.trim() }
    override suspend fun removeSenderId(id: String) { _senderIds.value = _senderIds.value - id }
    override suspend fun setDefaultCurrency(code: String) { _currency.value = code }
    override suspend fun setConfidenceThreshold(value: Float) { _threshold.value = value }
    override suspend fun setBubbleEnabled(enabled: Boolean) { _bubble.value = _bubble.value.copy(enabled = enabled) }
    override suspend fun setBubbleAutoHideSeconds(seconds: Int) { _bubble.value = _bubble.value.copy(autoHideSeconds = seconds) }
    override suspend fun setBubbleStartY(y: Int) { _bubble.value = _bubble.value.copy(startY = y) }
    override suspend fun setApiEnabled(enabled: Boolean) { _api.value = _api.value.copy(enabled = enabled) }
    override suspend fun setApiBaseUrl(url: String) { _api.value = _api.value.copy(baseUrl = url) }
    override suspend fun setApiAuthToken(token: String) { _api.value = _api.value.copy(authToken = token) }
    override suspend fun setSetupCompleted(completed: Boolean) { _setupCompleted.value = completed }
}

class FakeDeviceSmsSource(
    var inbox: List<com.smsexpense.tracker.domain.model.IncomingMessage> = emptyList(),
) : com.smsexpense.tracker.domain.source.DeviceSmsSource {

    override suspend fun recentMessages(limit: Int): List<com.smsexpense.tracker.domain.source.StoredSms> =
        inbox.sortedByDescending { it.timestamp }
            .take(limit)
            .map { com.smsexpense.tracker.domain.source.StoredSms(it.sender, it.body, it.timestamp) }

    override suspend fun messagesBetween(from: Long, to: Long): List<com.smsexpense.tracker.domain.model.IncomingMessage> =
        inbox.filter { it.timestamp in from..to }.sortedBy { it.timestamp }
}

class FakeImportHistoryRepository : com.smsexpense.tracker.domain.repository.ImportHistoryRepository {
    private val records = MutableStateFlow<List<com.smsexpense.tracker.domain.model.ImportRecord>>(emptyList())
    private var nextId = 1L

    override suspend fun record(fromDate: Long, toDate: Long, count: Int, total: Double, currency: String) {
        records.value = records.value + com.smsexpense.tracker.domain.model.ImportRecord(
            id = nextId++,
            importedAt = 0L,
            fromDate = fromDate,
            toDate = toDate,
            transactionCount = count,
            totalAmount = total,
            currency = currency,
        )
    }

    override fun observeAll(): Flow<List<com.smsexpense.tracker.domain.model.ImportRecord>> = records
}
