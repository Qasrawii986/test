package com.smsexpense.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsexpense.tracker.domain.repository.ApiSettings
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.BubbleShape
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
        val BUBBLE_START_X_PCT = floatPreferencesKey("bubble_start_x_pct")
        val BUBBLE_START_Y_PCT = floatPreferencesKey("bubble_start_y_pct")
        val BUBBLE_REMEMBER_POSITION = booleanPreferencesKey("bubble_remember_position")
        val BUBBLE_SNAP_TO_EDGE = booleanPreferencesKey("bubble_snap_to_edge")
        val BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        val BUBBLE_SHAPE = stringPreferencesKey("bubble_shape")
        val BUBBLE_COLOR = longPreferencesKey("bubble_color")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val BUBBLE_SHOW_AMOUNT = booleanPreferencesKey("bubble_show_amount")
        val API_ENABLED = booleanPreferencesKey("api_enabled")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_AUTH_TOKEN = stringPreferencesKey("api_auth_token")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val LAST_BUBBLE_STATUS = stringPreferencesKey("last_bubble_status")
        val BACK_TAP_ENABLED = booleanPreferencesKey("back_tap_enabled")
        val BACK_TAP_SENSITIVITY = stringPreferencesKey("back_tap_sensitivity")
        val PENDING_UPDATE_CODE = intPreferencesKey("pending_update_code")
        val PENDING_UPDATE_NAME = stringPreferencesKey("pending_update_name")
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
            startXPercent = it[Keys.BUBBLE_START_X_PCT] ?: BubbleSettings.DEFAULT_X_PERCENT,
            startYPercent = it[Keys.BUBBLE_START_Y_PCT] ?: BubbleSettings.DEFAULT_Y_PERCENT,
            rememberPosition = it[Keys.BUBBLE_REMEMBER_POSITION] ?: true,
            snapToEdge = it[Keys.BUBBLE_SNAP_TO_EDGE] ?: true,
            sizeDp = it[Keys.BUBBLE_SIZE_DP] ?: BubbleSettings.DEFAULT_SIZE_DP,
            shape = runCatching { BubbleShape.valueOf(it[Keys.BUBBLE_SHAPE] ?: "") }
                .getOrDefault(BubbleShape.CIRCLE),
            colorArgb = it[Keys.BUBBLE_COLOR]?.takeIf { value -> value != 0L },
            opacity = it[Keys.BUBBLE_OPACITY] ?: 1f,
            showAmount = it[Keys.BUBBLE_SHOW_AMOUNT] ?: true,
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

    override val backTapEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BACK_TAP_ENABLED] ?: false }

    override val backTapSensitivity: Flow<String> =
        context.dataStore.data.map { it[Keys.BACK_TAP_SENSITIVITY] ?: "MEDIUM" }

    override suspend fun setBackTapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACK_TAP_ENABLED] = enabled }
    }

    override suspend fun setBackTapSensitivity(name: String) {
        context.dataStore.edit { it[Keys.BACK_TAP_SENSITIVITY] = name }
    }

    override val pendingUpdateVersion: Flow<Pair<Int, String>> = context.dataStore.data.map {
        (it[Keys.PENDING_UPDATE_CODE] ?: 0) to (it[Keys.PENDING_UPDATE_NAME] ?: "")
    }

    override suspend fun setPendingUpdateVersion(versionCode: Int, versionName: String) {
        context.dataStore.edit {
            it[Keys.PENDING_UPDATE_CODE] = versionCode
            it[Keys.PENDING_UPDATE_NAME] = versionName
        }
    }

    override suspend fun clearPendingUpdateVersion() {
        context.dataStore.edit {
            it.remove(Keys.PENDING_UPDATE_CODE)
            it.remove(Keys.PENDING_UPDATE_NAME)
        }
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

    override suspend fun setBubblePosition(xPercent: Float, yPercent: Float) {
        context.dataStore.edit {
            it[Keys.BUBBLE_START_X_PCT] = xPercent.coerceIn(0f, 1f)
            it[Keys.BUBBLE_START_Y_PCT] = yPercent.coerceIn(0f, 1f)
        }
    }

    override suspend fun setBubbleRememberPosition(remember: Boolean) {
        context.dataStore.edit { it[Keys.BUBBLE_REMEMBER_POSITION] = remember }
    }

    override suspend fun setBubbleSnapToEdge(snap: Boolean) {
        context.dataStore.edit { it[Keys.BUBBLE_SNAP_TO_EDGE] = snap }
    }

    override suspend fun setBubbleSizeDp(sizeDp: Int) {
        context.dataStore.edit {
            it[Keys.BUBBLE_SIZE_DP] =
                sizeDp.coerceIn(BubbleSettings.MIN_SIZE_DP, BubbleSettings.MAX_SIZE_DP)
        }
    }

    override suspend fun setBubbleShape(shape: BubbleShape) {
        context.dataStore.edit { it[Keys.BUBBLE_SHAPE] = shape.name }
    }

    override suspend fun setBubbleColor(argb: Long?) {
        // 0 doubles as "follow the theme" so the key can simply be cleared.
        context.dataStore.edit {
            if (argb == null) it.remove(Keys.BUBBLE_COLOR) else it[Keys.BUBBLE_COLOR] = argb
        }
    }

    override suspend fun setBubbleOpacity(opacity: Float) {
        context.dataStore.edit {
            it[Keys.BUBBLE_OPACITY] = opacity.coerceIn(BubbleSettings.MIN_OPACITY, 1f)
        }
    }

    override suspend fun setBubbleShowAmount(show: Boolean) {
        context.dataStore.edit { it[Keys.BUBBLE_SHOW_AMOUNT] = show }
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
