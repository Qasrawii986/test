package com.smsexpense.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsexpense.tracker.domain.repository.ApiSettings
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object Keys {
        val SENDER_IDS = stringSetPreferencesKey("sender_ids")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_AUTO_HIDE_SECONDS = intPreferencesKey("bubble_auto_hide_seconds")
        val BUBBLE_START_Y = intPreferencesKey("bubble_start_y")
        val API_ENABLED = booleanPreferencesKey("api_enabled")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_AUTH_TOKEN = stringPreferencesKey("api_auth_token")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val LAST_BUBBLE_STATUS = stringPreferencesKey("last_bubble_status")
    }

    override val senderIds: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SENDER_IDS] ?: emptySet() }

    override val defaultCurrency: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_CURRENCY] ?: "JOD" }

    override val confidenceThreshold: Flow<Float> =
        context.dataStore.data.map { it[Keys.CONFIDENCE_THRESHOLD] ?: 0.5f }

    override val bubbleSettings: Flow<BubbleSettings> = context.dataStore.data.map {
        BubbleSettings(
            enabled = it[Keys.BUBBLE_ENABLED] ?: true,
            autoHideSeconds = it[Keys.BUBBLE_AUTO_HIDE_SECONDS] ?: 45,
            startY = it[Keys.BUBBLE_START_Y] ?: 300,
        )
    }

    override val apiSettings: Flow<ApiSettings> = context.dataStore.data.map {
        ApiSettings(
            enabled = it[Keys.API_ENABLED] ?: false,
            baseUrl = it[Keys.API_BASE_URL] ?: "",
            authToken = it[Keys.API_AUTH_TOKEN] ?: "",
        )
    }

    override val setupCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SETUP_COMPLETED] ?: false }

    override suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.SETUP_COMPLETED] = completed }
    }

    override val lastBubbleStatus: Flow<String> =
        context.dataStore.data.map { it[Keys.LAST_BUBBLE_STATUS] ?: "" }

    override suspend fun setLastBubbleStatus(status: String) {
        context.dataStore.edit { it[Keys.LAST_BUBBLE_STATUS] = status }
    }

    override suspend fun addSenderId(id: String) {
        val normalized = id.trim()
        if (normalized.isEmpty()) return
        context.dataStore.edit {
            it[Keys.SENDER_IDS] = (it[Keys.SENDER_IDS] ?: emptySet()) + normalized
        }
    }

    override suspend fun removeSenderId(id: String) {
        context.dataStore.edit {
            it[Keys.SENDER_IDS] = (it[Keys.SENDER_IDS] ?: emptySet()) - id
        }
    }

    override suspend fun setDefaultCurrency(code: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = code.trim().uppercase() }
    }

    override suspend fun setConfidenceThreshold(value: Float) {
        context.dataStore.edit { it[Keys.CONFIDENCE_THRESHOLD] = value.coerceIn(0f, 1f) }
    }

    override suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BUBBLE_ENABLED] = enabled }
    }

    override suspend fun setBubbleAutoHideSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.BUBBLE_AUTO_HIDE_SECONDS] = seconds.coerceIn(5, 600) }
    }

    override suspend fun setBubbleStartY(y: Int) {
        context.dataStore.edit { it[Keys.BUBBLE_START_Y] = y }
    }

    override suspend fun setApiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.API_ENABLED] = enabled }
    }

    override suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.API_BASE_URL] = url.trim() }
    }

    override suspend fun setApiAuthToken(token: String) {
        context.dataStore.edit { it[Keys.API_AUTH_TOKEN] = token.trim() }
    }
}
