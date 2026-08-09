package com.smsexpense.tracker.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentDetailUiState(
    val loading: Boolean = true,
    val payment: Payment? = null,
    val categories: List<Category> = emptyList(),
)

class PaymentDetailViewModel(
    private val paymentId: Long,
    private val paymentRepository: PaymentRepository,
    categoryRepository: CategoryRepository,
    private val onCategorized: suspend () -> Unit = {},
) : ViewModel() {

    val uiState: StateFlow<PaymentDetailUiState> = combine(
        paymentRepository.observeById(paymentId),
        categoryRepository.observeAll(),
    ) { payment, categories ->
        PaymentDetailUiState(loading = false, payment = payment, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentDetailUiState())

    fun setCategory(categoryId: Long) {
        viewModelScope.launch {
            paymentRepository.categorize(paymentId, categoryId)
            onCategorized()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            paymentRepository.delete(paymentId)
            onDone()
        }
    }
}
