package com.smsexpense.tracker.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.ImportRecord
import com.smsexpense.tracker.domain.model.PaymentCandidate
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.ImportHistoryRepository
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.domain.usecase.ImportHistoricalTransactionsUseCase
import com.smsexpense.tracker.domain.usecase.ImportSummary
import com.smsexpense.tracker.domain.usecase.ScanEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One reviewable transaction in the historical import list. */
data class ReviewItem(
    val candidate: PaymentCandidate,
    val selected: Boolean = true,
    val categoryId: Long? = null,
)

sealed class ImportStep {
    data object Setup : ImportStep()
    data class Scanning(val messagesScanned: Int, val transactionsFound: Int) : ImportStep()
    data class Review(val items: List<ReviewItem>, val alreadyImported: Int, val scanned: Int) : ImportStep() {
        val selectedCount: Int get() = items.count { it.selected }
        val selectedTotal: Double get() = items.filter { it.selected }.sumOf { it.candidate.amount }
        val allSelected: Boolean get() = items.isNotEmpty() && items.all { it.selected }
        val currency: String get() = items.firstOrNull()?.candidate?.currency ?: ""
    }
    data object Importing : ImportStep()
    data class Done(val summary: ImportSummary) : ImportStep()
}

data class HistoricalImportUiState(
    val step: ImportStep = ImportStep.Setup,
    val permissionGranted: Boolean = false,
    val configuredSenders: List<String> = emptyList(),
    val selectedSenders: Set<String> = emptySet(),
    val fromDate: Long = defaultFrom(),
    val toDate: Long = defaultTo(),
    val categories: List<Category> = emptyList(),
    val history: List<ImportRecord> = emptyList(),
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
) {
    companion object {
        fun defaultFrom(): Long = LocalDate.now().minusMonths(3)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun defaultTo(): Long = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }
}

class HistoricalImportViewModel(
    private val importUseCase: ImportHistoricalTransactionsUseCase,
    private val settingsRepository: SettingsRepository,
    categoryRepository: CategoryRepository,
    importHistoryRepository: ImportHistoryRepository,
    private val onImported: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoricalImportUiState())
    val uiState: StateFlow<HistoricalImportUiState> = _uiState

    init {
        viewModelScope.launch {
            val senders = settingsRepository.senderIds.first().sorted()
            _uiState.value = _uiState.value.copy(
                configuredSenders = senders,
                selectedSenders = senders.toSet(),
            )
        }
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
        viewModelScope.launch {
            importHistoryRepository.observeAll().collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(permissionGranted = granted)
    }

    fun setPresetMonths(months: Long) {
        _uiState.value = _uiState.value.copy(
            fromDate = LocalDate.now().minusMonths(months)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            toDate = HistoricalImportUiState.defaultTo(),
        )
    }

    fun setFromDate(millis: Long) {
        _uiState.value = _uiState.value.copy(fromDate = millis)
    }

    fun setToDate(millis: Long) {
        // Include the whole selected day.
        _uiState.value = _uiState.value.copy(toDate = millis + DAY_MS - 1)
    }

    fun toggleSender(sender: String) {
        val current = _uiState.value.selectedSenders
        _uiState.value = _uiState.value.copy(
            selectedSenders = if (sender in current) current - sender else current + sender
        )
    }

    fun scan() {
        val state = _uiState.value
        if (state.selectedSenders.isEmpty()) {
            _uiState.value = state.copy(error = "Select at least one Sender ID")
            return
        }
        if (state.fromDate > state.toDate) {
            _uiState.value = state.copy(error = "\"From\" date must be before \"To\" date")
            return
        }
        _uiState.value = state.copy(step = ImportStep.Scanning(0, 0), error = null)
        viewModelScope.launch {
            try {
                importUseCase.scan(state.fromDate, state.toDate, state.selectedSenders)
                    .collect { event ->
                        when (event) {
                            is ScanEvent.Progress -> _uiState.value = _uiState.value.copy(
                                step = ImportStep.Scanning(event.messagesScanned, event.transactionsFound)
                            )
                            is ScanEvent.Done -> _uiState.value = _uiState.value.copy(
                                step = ImportStep.Review(
                                    items = event.candidates.map { ReviewItem(it.candidate) },
                                    alreadyImported = event.alreadyImported,
                                    scanned = event.messagesScanned,
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    step = ImportStep.Setup,
                    error = "Scan failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun updateReview(transform: (ImportStep.Review) -> ImportStep.Review) {
        val step = _uiState.value.step
        if (step is ImportStep.Review) {
            _uiState.value = _uiState.value.copy(step = transform(step))
        }
    }

    fun toggleItem(index: Int) = updateReview { review ->
        review.copy(items = review.items.mapIndexed { i, item ->
            if (i == index) item.copy(selected = !item.selected) else item
        })
    }

    fun toggleSelectAll() = updateReview { review ->
        val target = !review.allSelected
        review.copy(items = review.items.map { it.copy(selected = target) })
    }

    fun setItemCategory(index: Int, categoryId: Long?) = updateReview { review ->
        review.copy(items = review.items.mapIndexed { i, item ->
            if (i == index) item.copy(categoryId = categoryId) else item
        })
    }

    /** Bulk assignment: applies [categoryId] to every currently selected item. */
    fun setCategoryForSelected(categoryId: Long?) = updateReview { review ->
        review.copy(items = review.items.map {
            if (it.selected) it.copy(categoryId = categoryId) else it
        })
    }

    fun requestImport() {
        val step = _uiState.value.step
        if (step is ImportStep.Review && step.selectedCount > 0) {
            _uiState.value = _uiState.value.copy(showConfirmDialog = true)
        }
    }

    fun dismissConfirm() {
        _uiState.value = _uiState.value.copy(showConfirmDialog = false)
    }

    fun confirmImport() {
        val state = _uiState.value
        val review = state.step as? ImportStep.Review ?: return
        _uiState.value = state.copy(showConfirmDialog = false, step = ImportStep.Importing)
        viewModelScope.launch {
            try {
                val selection = review.items
                    .filter { it.selected }
                    .map { it.candidate to it.categoryId }
                val summary = importUseCase.import(selection, state.fromDate, state.toDate)
                onImported()
                _uiState.value = _uiState.value.copy(step = ImportStep.Done(summary))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    step = review,
                    error = "Import failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    fun backToSetup() {
        _uiState.value = _uiState.value.copy(step = ImportStep.Setup, error = null)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
