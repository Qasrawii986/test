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
    /**
     * Idempotent: the same candidate (same dedup key) never creates a second row.
     * [categoryId] pre-categorizes the payment on insert (used by historical import,
     * where the user reviewed the category before confirming).
     */
    suspend fun ingest(
        candidate: PaymentCandidate,
        source: com.smsexpense.tracker.domain.model.PaymentSource =
            com.smsexpense.tracker.domain.model.PaymentSource.SMS_REALTIME,
        categoryId: Long? = null,
    ): IngestResult

    /** Which of these dedup keys already exist in the database. */
    suspend fun existingDedupKeys(keys: Collection<String>): Set<String>

    /** Cross-source duplicate guard (SMSC vs inbox timestamps differ for the same SMS). */
    suspend fun hasSimilar(sender: String, message: String, amount: Double, timestamp: Long, windowMs: Long): Boolean

    /** Same amount at nearly the same time, seen through a different channel. */
    suspend fun hasCrossSourceTwin(
        amount: Double,
        timestamp: Long,
        windowMs: Long,
        source: com.smsexpense.tracker.domain.model.PaymentSource,
    ): Boolean
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
    /** [parentId] null creates a root category; otherwise a subcategory of that root. */
    suspend fun add(name: String, icon: String, color: Long?, parentId: Long? = null): Long
    suspend fun update(category: Category)
    /** Deleting a root also deletes its subcategories; affected payments lose their link. */
    suspend fun delete(id: Long)
    /** Reorders within the category's own sibling group. */
    suspend fun move(id: Long, up: Boolean)
    suspend fun seedDefaultsIfEmpty()
}

enum class BubbleShape { CIRCLE, ROUNDED, SQUARE }

data class BubbleSettings(
    val enabled: Boolean,
    val autoHideSeconds: Int,
    /**
     * Default position as a fraction of the screen (0f..1f) rather than pixels,
     * so it survives different screen sizes and orientation changes.
     */
    val startXPercent: Float = DEFAULT_X_PERCENT,
    val startYPercent: Float = DEFAULT_Y_PERCENT,
    /** Dragging the bubble updates the default position. */
    val rememberPosition: Boolean = true,
    /** Release snaps the bubble to the nearest side edge. */
    val snapToEdge: Boolean = true,
    /** Diameter of the collapsed bubble, in dp. */
    val sizeDp: Int = DEFAULT_SIZE_DP,
    val shape: BubbleShape = BubbleShape.CIRCLE,
    /** ARGB colour, or null to follow the app theme. */
    val colorArgb: Long? = null,
    val opacity: Float = 1f,
    /** Show the amount inside the bubble, or just an icon. */
    val showAmount: Boolean = true,
) {
    companion object {
        const val DEFAULT_SIZE_DP = 64
        const val MIN_SIZE_DP = 40
        const val MAX_SIZE_DP = 96
        const val MIN_OPACITY = 0.3f
        const val DEFAULT_X_PERCENT = 0.02f
        const val DEFAULT_Y_PERCENT = 0.35f

        /** Preset swatches offered in settings; null means "follow the theme". */
        val PRESET_COLORS: List<Long?> = listOf(
            null,
            0xFF1B6E4F, 0xFF1565C0, 0xFF6A1B9A, 0xFFC62828,
            0xFFEF6C00, 0xFF00695C, 0xFF37474F, 0xFFAD1457,
        )
    }
}

data class ApiSettings(
    val enabled: Boolean,
    val baseUrl: String,
    val authToken: String,
)

interface ImportHistoryRepository {
    suspend fun record(fromDate: Long, toDate: Long, count: Int, total: Double, currency: String)
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.smsexpense.tracker.domain.model.ImportRecord>>
}

interface SettingsRepository {
    val senderIds: Flow<Set<String>>
    /** App packages whose notifications are scanned for payments. */
    val notificationPackages: Flow<Set<String>>
    val defaultCurrency: Flow<String>
    val confidenceThreshold: Flow<Float>
    val bubbleSettings: Flow<BubbleSettings>
    val apiSettings: Flow<ApiSettings>
    /** First-run setup wizard finished (or skipped). */
    val setupCompleted: Flow<Boolean>
    /** Why the last detected payment did or did not raise a bubble (diagnostics). */
    val lastBubbleStatus: Flow<String>
    /** Sensor-based triple back tap; opt-in because it costs battery. */
    val backTapEnabled: Flow<Boolean>
    val backTapSensitivity: Flow<String>
    /** (versionCode, versionName) handed to the installer; survives the process being replaced. */
    val pendingUpdateVersion: Flow<Pair<Int, String>>

    suspend fun addSenderId(id: String)
    suspend fun removeSenderId(id: String)
    suspend fun addNotificationPackage(packageName: String)
    suspend fun removeNotificationPackage(packageName: String)
    suspend fun setDefaultCurrency(code: String)
    suspend fun setConfidenceThreshold(value: Float)
    suspend fun setBubbleEnabled(enabled: Boolean)
    suspend fun setBubbleAutoHideSeconds(seconds: Int)
    suspend fun setBubblePosition(xPercent: Float, yPercent: Float)
    suspend fun setBubbleRememberPosition(remember: Boolean)
    suspend fun setBubbleSnapToEdge(snap: Boolean)
    suspend fun setBubbleSizeDp(sizeDp: Int)
    suspend fun setBubbleShape(shape: BubbleShape)
    suspend fun setBubbleColor(argb: Long?)
    suspend fun setBubbleOpacity(opacity: Float)
    suspend fun setBubbleShowAmount(show: Boolean)
    suspend fun setApiEnabled(enabled: Boolean)
    suspend fun setApiBaseUrl(url: String)
    suspend fun setApiAuthToken(token: String)
    suspend fun setSetupCompleted(completed: Boolean)
    suspend fun setLastBubbleStatus(status: String)
    suspend fun setBackTapEnabled(enabled: Boolean)
    suspend fun setBackTapSensitivity(name: String)
    suspend fun setPendingUpdateVersion(versionCode: Int, versionName: String)
    suspend fun clearPendingUpdateVersion()
}
