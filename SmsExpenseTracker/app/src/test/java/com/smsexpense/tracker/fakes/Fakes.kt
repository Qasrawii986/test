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

    override suspend fun hasCrossSourceTwin(
        amount: Double,
        timestamp: Long,
        windowMs: Long,
        source: PaymentSource,
    ): Boolean = payments.value.any {
        it.amount == amount &&
            kotlin.math.abs(it.timestamp - timestamp) <= windowMs &&
            it.source != source
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

    override suspend fun updateDetails(paymentId: Long, merchant: String?, amount: Double) {
        payments.value = payments.value.map {
            if (it.id == paymentId) it.copy(
                merchant = merchant?.trim()?.takeIf { name -> name.isNotEmpty() },
                amount = amount,
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

    override suspend fun add(name: String, icon: String, color: Long?, parentId: Long?): Long {
        // Mirror the real repository: never deeper than two levels.
        val effectiveParent = parentId?.let { requested ->
            categories.value.find { it.id == requested }?.let { it.parentId ?: it.id }
        }
        val category = Category(
            id = nextId++,
            name = name,
            icon = icon,
            color = color,
            sortOrder = categories.value.count { it.parentId == effectiveParent },
            createdAt = 0L,
            parentId = effectiveParent,
        )
        categories.value = categories.value + category
        return category.id
    }

    override suspend fun update(category: Category) {
        categories.value = categories.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: Long) {
        categories.value = categories.value.filterNot { it.id == id || it.parentId == id }
    }

    override suspend fun move(id: Long, up: Boolean) = Unit

    override suspend fun seedDefaultsIfEmpty() {
        if (categories.value.isEmpty()) add("Other", "📦", null, null)
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
    private val _bubble = MutableStateFlow(BubbleSettings(enabled = true, autoHideSeconds = 45))
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

    private val _notificationPackages = MutableStateFlow<Set<String>>(emptySet())
    override val notificationPackages: Flow<Set<String>> = _notificationPackages
    override suspend fun addNotificationPackage(packageName: String) {
        _notificationPackages.value = _notificationPackages.value + packageName.trim()
    }
    override suspend fun removeNotificationPackage(packageName: String) {
        _notificationPackages.value = _notificationPackages.value - packageName
    }
    override suspend fun setDefaultCurrency(code: String) { _currency.value = code }
    override suspend fun setConfidenceThreshold(value: Float) { _threshold.value = value }
    override suspend fun setBubbleEnabled(enabled: Boolean) { _bubble.value = _bubble.value.copy(enabled = enabled) }
    override suspend fun setBubbleAutoHideSeconds(seconds: Int) {
        // Mirrors the real repository: 0 is "never", anything else is clamped.
        _bubble.value = _bubble.value.copy(
            autoHideSeconds = if (seconds <= 0) {
                BubbleSettings.NEVER_AUTO_HIDE
            } else {
                seconds.coerceIn(
                    BubbleSettings.MIN_AUTO_HIDE_SECONDS,
                    BubbleSettings.MAX_AUTO_HIDE_SECONDS,
                )
            }
        )
    }
    override suspend fun setBubblePosition(xPercent: Float, yPercent: Float) {
        _bubble.value = _bubble.value.copy(
            startXPercent = xPercent.coerceIn(0f, 1f),
            startYPercent = yPercent.coerceIn(0f, 1f),
        )
    }
    override suspend fun setBubbleRememberPosition(remember: Boolean) {
        _bubble.value = _bubble.value.copy(rememberPosition = remember)
    }
    override suspend fun setBubbleSnapToEdge(snap: Boolean) {
        _bubble.value = _bubble.value.copy(snapToEdge = snap)
    }
    override suspend fun setBubbleSizeDp(sizeDp: Int) {
        _bubble.value = _bubble.value.copy(
            sizeDp = sizeDp.coerceIn(BubbleSettings.MIN_SIZE_DP, BubbleSettings.MAX_SIZE_DP)
        )
    }
    override suspend fun setBubbleShape(shape: com.smsexpense.tracker.domain.repository.BubbleShape) {
        _bubble.value = _bubble.value.copy(shape = shape)
    }
    override suspend fun setBubbleColor(argb: Long?) { _bubble.value = _bubble.value.copy(colorArgb = argb) }
    override suspend fun setBubbleOpacity(opacity: Float) {
        _bubble.value = _bubble.value.copy(
            opacity = opacity.coerceIn(BubbleSettings.MIN_OPACITY, 1f)
        )
    }
    override suspend fun setBubbleShowAmount(show: Boolean) {
        _bubble.value = _bubble.value.copy(showAmount = show)
    }
    override suspend fun setBubbleBackground(path: String?) {
        _bubble.value = _bubble.value.copy(backgroundPath = path?.takeIf { it.isNotBlank() })
    }
    override suspend fun setApiEnabled(enabled: Boolean) { _api.value = _api.value.copy(enabled = enabled) }
    override suspend fun setApiBaseUrl(url: String) { _api.value = _api.value.copy(baseUrl = url) }
    override suspend fun setApiAuthToken(token: String) { _api.value = _api.value.copy(authToken = token) }
    override suspend fun setSetupCompleted(completed: Boolean) { _setupCompleted.value = completed }

    private val _language = MutableStateFlow("system")
    override val language: Flow<String> = _language
    override suspend fun setLanguage(language: String) { _language.value = language }

    private val _lastBubbleStatus = MutableStateFlow("")
    override val lastBubbleStatus: Flow<String> = _lastBubbleStatus
    override suspend fun setLastBubbleStatus(status: String) { _lastBubbleStatus.value = status }

    private val _backTapEnabled = MutableStateFlow(false)
    private val _backTapSensitivity = MutableStateFlow("MEDIUM")
    override val backTapEnabled: Flow<Boolean> = _backTapEnabled
    override val backTapSensitivity: Flow<String> = _backTapSensitivity
    override suspend fun setBackTapEnabled(enabled: Boolean) { _backTapEnabled.value = enabled }
    override suspend fun setBackTapSensitivity(name: String) { _backTapSensitivity.value = name }

    private val _pendingUpdate = MutableStateFlow(0 to "")
    override val pendingUpdateVersion: Flow<Pair<Int, String>> = _pendingUpdate
    override suspend fun setPendingUpdateVersion(versionCode: Int, versionName: String) {
        _pendingUpdate.value = versionCode to versionName
    }
    override suspend fun clearPendingUpdateVersion() { _pendingUpdate.value = 0 to "" }
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

/**
 * In-memory split repository mirroring the Room one's rules: allocations are
 * only ever recorded for payers other than yourself, so "my share" stays the
 * remainder of the payment.
 */
class FakeSplitRepository : com.smsexpense.tracker.domain.repository.SplitRepository {

    private val payers = MutableStateFlow<List<com.smsexpense.tracker.domain.model.Payer>>(emptyList())
    private val allocations =
        MutableStateFlow<List<com.smsexpense.tracker.domain.model.Allocation>>(emptyList())
    /** Payment timestamps, so period-scoped queries can be exercised. */
    private val timestamps = mutableMapOf<Long, Long>()
    /** Category of each payment, for the per-category breakdown. */
    private val categoryOf = mutableMapOf<Long, Long?>()
    private var nextPayerId = 1L
    private var nextAllocationId = 1L

    fun registerPayment(paymentId: Long, timestamp: Long, categoryId: Long? = null) {
        timestamps[paymentId] = timestamp
        categoryOf[paymentId] = categoryId
    }

    fun allocationsOf(paymentId: Long) = allocations.value.filter { it.paymentId == paymentId }

    override fun observePayers(): Flow<List<com.smsexpense.tracker.domain.model.Payer>> = payers

    override suspend fun getPayers(): List<com.smsexpense.tracker.domain.model.Payer> = payers.value

    override suspend fun selfPayerId(): Long? = payers.value.find { it.isSelf }?.id

    override suspend fun seedSelfIfEmpty() {
        if (payers.value.any { it.isSelf }) return
        payers.value = payers.value + com.smsexpense.tracker.domain.model.Payer(
            id = nextPayerId++, name = "You", emoji = "🙋", color = null,
            isSelf = true, sortOrder = 0, createdAt = 0L,
        )
    }

    override suspend fun addPayer(name: String, emoji: String, color: Long?): Long {
        val payer = com.smsexpense.tracker.domain.model.Payer(
            id = nextPayerId++, name = name.trim(), emoji = emoji.ifBlank { "👤" },
            color = color, isSelf = false, sortOrder = payers.value.size, createdAt = 0L,
        )
        payers.value = payers.value + payer
        return payer.id
    }

    override suspend fun updatePayer(payer: com.smsexpense.tracker.domain.model.Payer) {
        payers.value = payers.value.map {
            if (it.id == payer.id) payer.copy(isSelf = it.isSelf) else it
        }
    }

    override suspend fun deletePayer(id: Long) {
        if (payers.value.find { it.id == id }?.isSelf == true) return
        payers.value = payers.value.filterNot { it.id == id }
        allocations.value = allocations.value.filterNot { it.payerId == id }
    }

    override suspend fun chargedCount(payerId: Long): Int =
        allocations.value.count { it.payerId == payerId }

    override fun observeSplit(
        paymentId: Long,
        total: Double,
        currency: String,
    ): Flow<com.smsexpense.tracker.domain.model.PaymentSplit> = allocations.map { list ->
        com.smsexpense.tracker.domain.model.PaymentSplit(
            paymentId, total, currency, list.filter { it.paymentId == paymentId },
        )
    }

    override suspend fun getSplit(
        paymentId: Long,
        total: Double,
        currency: String,
    ) = com.smsexpense.tracker.domain.model.PaymentSplit(
        paymentId, total, currency, allocationsOf(paymentId),
    )

    override suspend fun setSplit(paymentId: Long, amountsByPayer: Map<Long, Double>) {
        val selfId = selfPayerId()
        val previouslySettled = allocationsOf(paymentId).filter { it.settled }
            .associate { it.payerId to it.amount }
        val wanted = amountsByPayer
            .filterKeys { it != selfId }
            .filterValues { it > com.smsexpense.tracker.domain.model.PaymentSplit.CENT }
        allocations.value = allocations.value.filterNot { it.paymentId == paymentId } +
            wanted.map { (payerId, amount) ->
                com.smsexpense.tracker.domain.model.Allocation(
                    id = nextAllocationId++,
                    paymentId = paymentId,
                    payerId = payerId,
                    amount = amount,
                    settled = previouslySettled[payerId]?.let {
                        kotlin.math.abs(it - amount) <
                            com.smsexpense.tracker.domain.model.PaymentSplit.CENT
                    } ?: false,
                )
            }
    }

    override suspend fun chargeWholePayment(paymentId: Long, payerId: Long, total: Double) {
        if (payerId == selfPayerId()) {
            allocations.value = allocations.value.filterNot { it.paymentId == paymentId }
            return
        }
        setSplit(paymentId, mapOf(payerId to total))
    }

    private fun inPeriod(paymentId: Long, from: Long, to: Long): Boolean {
        val ts = timestamps[paymentId] ?: return true
        return ts >= from && ts < to
    }

    override fun observeOwedBetween(
        from: Long,
        to: Long,
    ): Flow<List<com.smsexpense.tracker.domain.model.OwedTotal>> = allocations.map { list ->
        list.filter { !it.settled && inPeriod(it.paymentId, from, to) }
            .groupBy { it.payerId }
            .map { (payerId, group) ->
                com.smsexpense.tracker.domain.model.OwedTotal(
                    payerId, group.sumOf { it.amount }, group.size,
                )
            }
    }

    override fun observeOwedAllTime(): Flow<List<com.smsexpense.tracker.domain.model.OwedTotal>> =
        allocations.map { list ->
            list.filter { !it.settled }
                .groupBy { it.payerId }
                .map { (payerId, group) ->
                    com.smsexpense.tracker.domain.model.OwedTotal(
                        payerId, group.sumOf { it.amount }, group.size,
                    )
                }
        }

    override fun observeChargedToOthersBetween(from: Long, to: Long): Flow<Double> =
        allocations.map { list ->
            list.filter { inPeriod(it.paymentId, from, to) }.sumOf { it.amount }
        }

    override fun observeChargedToOthersByCategoryBetween(
        from: Long,
        to: Long,
    ): Flow<List<com.smsexpense.tracker.domain.model.CategoryTotal>> = allocations.map { list ->
        list.filter { inPeriod(it.paymentId, from, to) }
            .groupBy { categoryOf[it.paymentId] }
            .map { (categoryId, group) ->
                CategoryTotal(categoryId, group.sumOf { it.amount }, group.size)
            }
    }

    override fun observeSharedPaymentIdsBetween(from: Long, to: Long): Flow<Set<Long>> =
        allocations.map { list ->
            list.filter { inPeriod(it.paymentId, from, to) }.map { it.paymentId }.toSet()
        }

    override suspend fun settleAllFor(payerId: Long) {
        allocations.value = allocations.value.map {
            if (it.payerId == payerId) it.copy(settled = true) else it
        }
    }

    override suspend fun unsettleAllFor(payerId: Long) {
        allocations.value = allocations.value.map {
            if (it.payerId == payerId) it.copy(settled = false) else it
        }
    }

    override suspend fun setSettled(allocationId: Long, settled: Boolean) {
        allocations.value = allocations.value.map {
            if (it.id == allocationId) it.copy(settled = settled) else it
        }
    }
}
