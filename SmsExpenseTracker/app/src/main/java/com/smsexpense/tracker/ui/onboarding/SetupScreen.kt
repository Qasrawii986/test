package com.smsexpense.tracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

class SetupViewModel(private val settings: SettingsRepository) : ViewModel() {

    val senderIds: StateFlow<List<String>> = settings.senderIds
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSenderId(id: String) {
        viewModelScope.launch { settings.addSenderId(id) }
    }

    fun completeSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setSetupCompleted(true)
            onDone()
        }
    }
}

/**
 * First-run wizard: configure the bank Sender ID (from SMS or manually), then
 * optionally jump into the historical import. Entirely skippable — the daily
 * flow works without it and everything here is reachable later from Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onChooseFromSms: () -> Unit,
    onImportHistorical: () -> Unit,
    onFinish: () -> Unit,
) {
    val senders by viewModel.senderIds.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.setup_lets_configure), style = MaterialTheme.typography.bodyLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_step1), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.setup_step1_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (senders.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        senders.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                OutlinedButton(onClick = onChooseFromSms, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_choose_from_sms))
                }
                var manual by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        label = { Text(stringResource(R.string.setup_manual_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.addSenderId(manual)
                            manual = ""
                        },
                        enabled = manual.isNotBlank(),
                    ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_sender)) }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_step2), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.setup_step2_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onImportHistorical,
                    enabled = senders.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.setup_import_button)) }
                if (senders.isEmpty()) {
                    Text(
                        stringResource(R.string.setup_need_sender),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.completeSetup(onFinish) }) { Text(stringResource(R.string.skip_for_now)) }
            Button(
                onClick = { viewModel.completeSetup(onFinish) },
                enabled = senders.isNotEmpty(),
            ) { Text(stringResource(R.string.done)) }
        }
    }
}
