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
import java.time.YearMonth

data class DashboardUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val stats: MonthlyStats? = null,
    val payments: List<Payment> = emptyList(),
    val uncategorized: List<Payment> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val paymentRepository: PaymentRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<DashboardUiState> = selectedMonth.flatMapLatest { month ->
        combine(
            paymentRepository.observeMonthlyStats(month.year, month.monthValue),
            paymentRepository.observeMonth(month.year, month.monthValue),
            paymentRepository.observeUncategorized(),
            categoryRepository.observeAll(),
        ) { stats, payments, uncategorized, categories ->
            DashboardUiState(
                loading = false,
                month = month,
                stats = stats,
                payments = payments,
                uncategorized = uncategorized,
                categories = categories,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) selectedMonth.value = next
    }
}
