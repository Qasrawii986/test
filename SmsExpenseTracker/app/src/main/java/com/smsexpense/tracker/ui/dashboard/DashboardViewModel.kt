package com.smsexpense.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.MonthlyStats
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.PaymentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class DashboardUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val stats: MonthlyStats? = null,
    val payments: List<Payment> = emptyList(),
    val uncategorized: List<Payment> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payers: List<com.smsexpense.tracker.domain.model.Payer> = emptyList(),
    /** Part of this month's spending charged to other people. */
    val chargedToOthers: Double = 0.0,
    val owed: List<com.smsexpense.tracker.domain.model.OwedTotal> = emptyList(),
    /** Charged to others, per category, so the breakdown can show your share. */
    val chargedToOthersByCategory: List<com.smsexpense.tracker.domain.model.CategoryTotal> = emptyList(),
    val sharedPaymentIds: Set<Long> = emptySet(),
) {
    /** What the month actually cost you. */
    val myShare: Double
        get() = ((stats?.total ?: 0.0) - chargedToOthers).coerceAtLeast(0.0)

    val hasSharing: Boolean get() = chargedToOthers > 0.0 || owed.any { it.amount > 0.0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val paymentRepository: PaymentRepository,
    categoryRepository: CategoryRepository,
    private val splitRepository: com.smsexpense.tracker.domain.repository.SplitRepository,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<DashboardUiState> = selectedMonth.flatMapLatest { month ->
        val (from, to) = com.smsexpense.tracker.data.repository.RoomPaymentRepository
            .monthBounds(month.year, month.monthValue)
        combine(
            paymentRepository.observeMonthlyStats(month.year, month.monthValue),
            paymentRepository.observeMonth(month.year, month.monthValue),
            paymentRepository.observeUncategorized(),
            categoryRepository.observeAll(),
            // Sharing is four more streams; grouped so the top-level combine
            // stays within its arity and the state stays a single snapshot.
            combine(
                splitRepository.observePayers(),
                splitRepository.observeChargedToOthersBetween(from, to),
                splitRepository.observeOwedBetween(from, to),
                splitRepository.observeChargedToOthersByCategoryBetween(from, to),
                splitRepository.observeSharedPaymentIdsBetween(from, to),
            ) { payers, charged, owed, chargedByCategory, sharedIds ->
                Sharing(payers, charged, owed, chargedByCategory, sharedIds)
            },
        ) { stats, payments, uncategorized, categories, sharing ->
            DashboardUiState(
                loading = false,
                month = month,
                stats = stats,
                payments = payments,
                uncategorized = uncategorized,
                categories = categories,
                payers = sharing.payers,
                chargedToOthers = sharing.chargedToOthers,
                owed = sharing.owed,
                chargedToOthersByCategory = sharing.chargedByCategory,
                sharedPaymentIds = sharing.sharedIds,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private data class Sharing(
        val payers: List<com.smsexpense.tracker.domain.model.Payer>,
        val chargedToOthers: Double,
        val owed: List<com.smsexpense.tracker.domain.model.OwedTotal>,
        val chargedByCategory: List<com.smsexpense.tracker.domain.model.CategoryTotal>,
        val sharedIds: Set<Long>,
    )

    fun settle(payerId: Long) {
        viewModelScope.launch { splitRepository.settleAllFor(payerId) }
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) selectedMonth.value = next
    }
}
