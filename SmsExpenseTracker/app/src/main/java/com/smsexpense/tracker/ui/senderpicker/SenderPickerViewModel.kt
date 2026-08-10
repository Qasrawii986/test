package com.smsexpense.tracker.ui.senderpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.domain.source.DeviceSmsSource
import com.smsexpense.tracker.domain.source.StoredSms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SenderPickerUiState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val messages: List<StoredSms> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val query: String = "",
    val configuredSenders: Set<String> = emptySet(),
    /** Distinct senders extracted from the selection, awaiting user confirmation. */
    val pendingSenders: List<String>? = null,
    val addedCount: Int? = null,
) {
    val filteredIndices: List<Int>
        get() = messages.indices.filter { i ->
            query.isBlank() ||
                messages[i].sender.contains(query, ignoreCase = true) ||
                messages[i].body.contains(query, ignoreCase = true)
        }
    val selectedCount: Int get() = selected.size
}

class SenderPickerViewModel(
    private val smsSource: DeviceSmsSource,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SenderPickerUiState())
    val uiState: StateFlow<SenderPickerUiState> = _uiState

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(permissionGranted = granted)
        if (granted && _uiState.value.messages.isEmpty()) load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val configured = settingsRepository.senderIds.first()
            val messages = smsSource.recentMessages(RECENT_LIMIT)
            _uiState.value = _uiState.value.copy(
                loading = false,
                messages = messages,
                configuredSenders = configured,
                selected = emptySet(),
            )
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun toggle(index: Int) {
        val current = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (index in current) current - index else current + index
        )
    }

    /**
     * Distinct sender IDs from the selected messages. One sender → added directly;
     * several → surfaced as [SenderPickerUiState.pendingSenders] for the user to
     * confirm which ones to keep.
     */
    fun addSelected() {
        val state = _uiState.value
        val senders = state.selected
            .mapNotNull { state.messages.getOrNull(it)?.sender?.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
        if (senders.isEmpty()) return
        if (senders.size == 1) {
            confirmSenders(senders)
        } else {
            _uiState.value = state.copy(pendingSenders = senders)
        }
    }

    fun dismissPending() {
        _uiState.value = _uiState.value.copy(pendingSenders = null)
    }

    fun confirmSenders(senders: List<String>) {
        viewModelScope.launch {
            senders.forEach { settingsRepository.addSenderId(it) }
            val configured = settingsRepository.senderIds.first()
            _uiState.value = _uiState.value.copy(
                pendingSenders = null,
                selected = emptySet(),
                configuredSenders = configured,
                addedCount = senders.size,
            )
        }
    }

    fun consumeAddedMessage() {
        _uiState.value = _uiState.value.copy(addedCount = null)
    }

    companion object {
        const val RECENT_LIMIT = 500
    }
}
