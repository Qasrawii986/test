package com.smsexpense.tracker.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentSplit
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.PaymentRepository
import com.smsexpense.tracker.domain.repository.SplitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentDetailUiState(
    val loading: Boolean = true,
    val payment: Payment? = null,
    val categories: List<Category> = emptyList(),
    val payers: List<Payer> = emptyList(),
    val split: PaymentSplit? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentDetailViewModel(
    private val paymentId: Long,
    private val paymentRepository: PaymentRepository,
    categoryRepository: CategoryRepository,
    private val splitRepository: SplitRepository,
    private val onCategorized: suspend () -> Unit = {},
) : ViewModel() {

    // The split's total has to follow the payment, so it is derived from it
    // rather than combined alongside it.
    private val paymentWithSplit = paymentRepository.observeById(paymentId)
        .flatMapLatest { payment ->
            if (payment == null) {
                flowOf<Pair<Payment?, PaymentSplit?>>(null to null)
            } else {
                splitRepository.observeSplit(paymentId, payment.amount, payment.currency)
                    .map { split -> payment to split }
            }
        }

    val uiState: StateFlow<PaymentDetailUiState> = combine(
        paymentWithSplit,
        categoryRepository.observeAll(),
        splitRepository.observePayers(),
    ) { (payment, split), categories, payers ->
        PaymentDetailUiState(
            loading = false,
            payment = payment,
            categories = categories,
            payers = payers,
            split = split,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentDetailUiState())

    fun setCategory(categoryId: Long) {
        viewModelScope.launch {
            paymentRepository.categorize(paymentId, categoryId)
            onCategorized()
        }
    }

    fun updateDetails(merchant: String?, amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch { paymentRepository.updateDetails(paymentId, merchant, amount) }
    }

    fun chargeWholeTo(payerId: Long) {
        val total = uiState.value.payment?.amount ?: return
        viewModelScope.launch { splitRepository.chargeWholePayment(paymentId, payerId, total) }
    }

    fun saveSplit(amountsByPayer: Map<Long, Double>) {
        viewModelScope.launch { splitRepository.setSplit(paymentId, amountsByPayer) }
    }

    fun setAllocationSettled(allocationId: Long, settled: Boolean) {
        viewModelScope.launch { splitRepository.setSettled(allocationId, settled) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            paymentRepository.delete(paymentId)
            onDone()
        }
    }
}
