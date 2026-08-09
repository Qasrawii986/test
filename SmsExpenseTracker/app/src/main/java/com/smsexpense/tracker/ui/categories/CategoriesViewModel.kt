package com.smsexpense.tracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
)

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = categoryRepository.observeAll()
        .map { CategoriesUiState(loading = false, categories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun add(name: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.add(name, icon.ifBlank { "📦" }, color = null)
        }
    }

    fun rename(category: Category, name: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.update(category.copy(name = name, icon = icon.ifBlank { category.icon }))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { categoryRepository.delete(id) }
    }

    fun move(id: Long, up: Boolean) {
        viewModelScope.launch { categoryRepository.move(id, up) }
    }
}
