package com.smsexpense.tracker.ui.notifications

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.service.notification.PaymentNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

data class NotificationSourceUiState(
    val accessGranted: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val watched: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = true,
) {
    val visibleApps: List<InstalledApp>
        get() = apps
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            // Watched apps float to the top so the current selection is obvious.
            .sortedWith(compareByDescending<InstalledApp> { it.packageName in watched }.thenBy { it.label })
}

class NotificationSourceViewModel(
    application: Application,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NotificationSourceUiState())
    val uiState: StateFlow<NotificationSourceUiState> = _uiState

    init {
        viewModelScope.launch {
            settings.notificationPackages.collect { watched ->
                _uiState.value = _uiState.value.copy(watched = watched)
            }
        }
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getInstalledApplications(0)
                    // Launchable apps only: system services never post payment notifications.
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { info: ApplicationInfo ->
                        InstalledApp(info.packageName, pm.getApplicationLabel(info).toString())
                    }
                    .distinctBy { it.packageName }
            }
            _uiState.value = _uiState.value.copy(apps = apps, loading = false)
        }
    }

    fun refreshAccess() {
        _uiState.value = _uiState.value.copy(
            accessGranted = PaymentNotificationListener.isEnabled(getApplication())
        )
    }

    fun setQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun toggle(packageName: String) {
        viewModelScope.launch {
            if (packageName in _uiState.value.watched) settings.removeNotificationPackage(packageName)
            else settings.addNotificationPackage(packageName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSourceScreen(
    viewModel: NotificationSourceViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.accessGranted) "✅ Notification access granted"
                        else "⚠️ Notification access needed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Contactless taps cannot be read directly — Android gives the " +
                            "transaction only to the wallet app. Reading the payment " +
                            "notification your wallet or bank posts is the closest signal, " +
                            "and it usually arrives before the SMS.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!state.accessGranted) {
                        Button(onClick = {
                            context.startActivity(PaymentNotificationListener.settingsIntent())
                        }) { Text("Grant notification access") }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "Pick your wallet and bank apps (${state.watched.size} selected)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(16.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visibleApps, key = { it.packageName }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggle(app.packageName) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = app.packageName in state.watched,
                            onCheckedChange = { viewModel.toggle(app.packageName) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
