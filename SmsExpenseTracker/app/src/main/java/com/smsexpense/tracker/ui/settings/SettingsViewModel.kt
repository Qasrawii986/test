package com.smsexpense.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.repository.ApiSettings
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val senderIds: List<String> = emptyList(),
    val defaultCurrency: String = "JOD",
    val confidenceThreshold: Float = 0.5f,
    val bubble: BubbleSettings = BubbleSettings(enabled = true, autoHideSeconds = 45),
    val api: ApiSettings = ApiSettings(enabled = false, baseUrl = "", authToken = ""),
    val lastBubbleStatus: String = "",
    val language: String = com.smsexpense.tracker.util.AppLocale.SYSTEM,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.senderIds,
        settings.defaultCurrency,
        settings.confidenceThreshold,
        settings.bubbleSettings,
        settings.apiSettings,
        settings.lastBubbleStatus,
        settings.language,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            loading = false,
            senderIds = (values[0] as Set<String>).sorted(),
            defaultCurrency = values[1] as String,
            confidenceThreshold = values[2] as Float,
            bubble = values[3] as com.smsexpense.tracker.domain.repository.BubbleSettings,
            api = values[4] as ApiSettings,
            lastBubbleStatus = values[5] as String,
            language = values[6] as String,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addSenderId(id: String) = launch { settings.addSenderId(id) }
    fun removeSenderId(id: String) = launch { settings.removeSenderId(id) }
    fun setDefaultCurrency(code: String) = launch { settings.setDefaultCurrency(code) }

    /**
     * Persists the language, updates the cached locale, then asks the caller to
     * recreate so every already-composed screen picks up the new resources.
     */
    fun setLanguage(language: String, onApplied: () -> Unit) {
        viewModelScope.launch {
            settings.setLanguage(language)
            com.smsexpense.tracker.util.AppLocale.prime(language)
            onApplied()
        }
    }
    fun setConfidenceThreshold(value: Float) = launch { settings.setConfidenceThreshold(value) }
    fun setBubbleEnabled(enabled: Boolean) = launch { settings.setBubbleEnabled(enabled) }
    fun setBubbleAutoHide(seconds: Int) = launch { settings.setBubbleAutoHideSeconds(seconds) }
    fun setBubbleSize(sizeDp: Int) = launch { settings.setBubbleSizeDp(sizeDp) }
    fun setBubblePosition(xPercent: Float, yPercent: Float) =
        launch { settings.setBubblePosition(xPercent, yPercent) }
    fun setBubbleRememberPosition(remember: Boolean) =
        launch { settings.setBubbleRememberPosition(remember) }
    fun setBubbleSnapToEdge(snap: Boolean) = launch { settings.setBubbleSnapToEdge(snap) }
    fun setBubbleShape(shape: com.smsexpense.tracker.domain.repository.BubbleShape) =
        launch { settings.setBubbleShape(shape) }
    fun setBubbleColor(argb: Long?) = launch { settings.setBubbleColor(argb) }
    fun setBubbleOpacity(opacity: Float) = launch { settings.setBubbleOpacity(opacity) }
    fun setBubbleShowAmount(show: Boolean) = launch { settings.setBubbleShowAmount(show) }
    fun setApiEnabled(enabled: Boolean) = launch { settings.setApiEnabled(enabled) }
    fun setApiBaseUrl(url: String) = launch { settings.setApiBaseUrl(url) }
    fun setApiAuthToken(token: String) = launch { settings.setApiAuthToken(token) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
