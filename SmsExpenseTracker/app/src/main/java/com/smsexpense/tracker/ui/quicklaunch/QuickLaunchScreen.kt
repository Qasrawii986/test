package com.smsexpense.tracker.ui.quicklaunch

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchId
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchOption
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchState
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.service.quicklaunch.BackTapSensitivity
import com.smsexpense.tracker.service.quicklaunch.BackTapService
import com.smsexpense.tracker.service.quicklaunch.QuickLaunchRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuickLaunchUiState(
    val options: List<QuickLaunchOption> = emptyList(),
    val backTapEnabled: Boolean = false,
    val sensitivity: BackTapSensitivity = BackTapSensitivity.MEDIUM,
)

class QuickLaunchViewModel(
    application: Application,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QuickLaunchUiState())
    val uiState: StateFlow<QuickLaunchUiState> = _uiState

    init {
        viewModelScope.launch {
            settings.backTapEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(backTapEnabled = enabled)
                refresh()
            }
        }
        viewModelScope.launch {
            settings.backTapSensitivity.collect { name ->
                _uiState.value = _uiState.value.copy(
                    sensitivity = BackTapSensitivity.fromName(name)
                )
            }
        }
    }

    fun refresh() {
        val context: Context = getApplication()
        _uiState.value = _uiState.value.copy(
            options = QuickLaunchRegistry.describeAll(context, _uiState.value.backTapEnabled)
        )
    }

    fun onAction(id: QuickLaunchId, activity: Activity?) {
        val context: Context = getApplication()
        if (id == QuickLaunchId.SENSOR_BACK_TAP) {
            viewModelScope.launch {
                val enable = !settings.backTapEnabled.first()
                settings.setBackTapEnabled(enable)
                if (enable) BackTapService.start(context) else BackTapService.stop(context)
            }
            return
        }
        QuickLaunchRegistry.byId(id)?.activate(context, activity)
    }

    fun setSensitivity(value: BackTapSensitivity) {
        viewModelScope.launch { settings.setBackTapSensitivity(value.name) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLaunchScreen(
    viewModel: QuickLaunchViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick launch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ways to open quick actions from anywhere. Only what your device actually " +
                    "supports is listed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            state.options.forEach { option ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            when (option.state) {
                                QuickLaunchState.ACTIVE -> Text(
                                    "● On",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                else -> Unit
                            }
                        }
                        Text(option.description, style = MaterialTheme.typography.bodyMedium)
                        option.warning?.let {
                            Text(
                                "⚠ $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (option.supported) {
                            OutlinedButton(
                                onClick = { viewModel.onAction(option.id, activity) },
                            ) { Text(option.actionLabel) }

                            if (option.id == QuickLaunchId.SENSOR_BACK_TAP && state.backTapEnabled) {
                                Text("Sensitivity", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BackTapSensitivity.entries.forEach { level ->
                                        FilterChip(
                                            selected = state.sensitivity == level,
                                            onClick = { viewModel.setSensitivity(level) },
                                            label = { Text(level.name.lowercase()) },
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                option.unsupportedReason ?: "Not supported on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
