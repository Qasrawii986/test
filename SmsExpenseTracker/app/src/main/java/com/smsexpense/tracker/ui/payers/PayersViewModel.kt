package com.smsexpense.tracker.ui.payers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.domain.repository.SplitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A payer plus what they still owe you, all time. */
data class PayerRow(
    val payer: Payer,
    val outstanding: Double,
    val outstandingCount: Int,
)

data class PayersUiState(
    val loading: Boolean = true,
    val rows: List<PayerRow> = emptyList(),
    val currency: String = "JOD",
)

class PayersViewModel(
    private val splitRepository: SplitRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<PayersUiState> = combine(
        splitRepository.observePayers(),
        splitRepository.observeOwedAllTime(),
        settingsRepository.defaultCurrency,
    ) { payers, owed, currency ->
        val owedById = owed.associateBy { it.payerId }
        PayersUiState(
            loading = false,
            rows = payers.map { payer ->
                val row = owedById[payer.id]
                PayerRow(
                    payer = payer,
                    outstanding = row?.amount ?: 0.0,
                    outstandingCount = row?.count ?: 0,
                )
            },
            currency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayersUiState())

    init {
        // The screen may be the first thing opened after an update, before the
        // Application's own seeding has run.
        viewModelScope.launch { splitRepository.seedSelfIfEmpty() }
    }

    fun add(name: String, emoji: String) {
        if (name.isBlank()) return
        viewModelScope.launch { splitRepository.addPayer(name, emoji, null) }
    }

    fun update(payer: Payer, name: String, emoji: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            splitRepository.updatePayer(payer.copy(name = name, emoji = emoji))
        }
    }

    fun delete(payerId: Long) {
        viewModelScope.launch { splitRepository.deletePayer(payerId) }
    }

    fun settle(payerId: Long) {
        viewModelScope.launch { splitRepository.settleAllFor(payerId) }
    }

    fun reopen(payerId: Long) {
        viewModelScope.launch { splitRepository.unsettleAllFor(payerId) }
    }

    /** Used by the delete confirmation so the warning states a real number. */
    suspend fun chargedCount(payerId: Long): Int = splitRepository.chargedCount(payerId)
}
