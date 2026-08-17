package com.smsexpense.tracker.ui.senderpicker

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smsexpense.tracker.ui.components.formatDateTime
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

/**
 * In-app SMS picker. Android has no system intent for "pick an SMS", so this
 * screen lists the device inbox (READ_SMS) and lets the user multi-select
 * messages; distinct sender IDs are extracted and added to settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderPickerScreen(
    viewModel: SenderPickerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(state.addedCount) {
        state.addedCount?.let { count ->
            snackbar.showSnackbar(
                if (count == 1) "Sender ID added" else "$count Sender IDs added"
            )
            viewModel.consumeAddedMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.permissionGranted && state.messages.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.picker_selected, state.selectedCount),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = viewModel::addSelected,
                            enabled = state.selectedCount > 0,
                        ) { Text(stringResource(R.string.picker_add_selected)) }
                    }
                }
            }
        },
    ) { padding ->
        when {
            !state.permissionGranted -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.picker_needs_access), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.picker_needs_access_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                        Text(stringResource(R.string.picker_grant))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text(stringResource(R.string.picker_manual_instead)) }
                }
            }
            state.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }
            state.messages.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Text(stringResource(R.string.picker_no_sms)) }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.picker_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp, end = 16.dp, bottom = 16.dp,
                        ),
                    ) {
                        items(state.filteredIndices, key = { it }) { index ->
                            val sms = state.messages[index]
                            val alreadyConfigured = state.configuredSenders.any {
                                it.equals(sms.sender.trim(), ignoreCase = true)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggle(index) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = index in state.selected,
                                    onCheckedChange = { viewModel.toggle(index) },
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            sms.sender,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (alreadyConfigured) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.picker_already_added),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Text(
                                        sms.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        formatDateTime(sms.timestamp),
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
    }

    state.pendingSenders?.let { pending ->
        var checked by remember(pending) { mutableStateOf(pending.toSet()) }
        AlertDialog(
            onDismissRequest = viewModel::dismissPending,
            title = { Text(stringResource(R.string.picker_senders_found)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.picker_senders_prompt, pending.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    pending.forEach { sender ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = if (sender in checked) checked - sender else checked + sender
                                },
                        ) {
                            Checkbox(
                                checked = sender in checked,
                                onCheckedChange = {
                                    checked = if (sender in checked) checked - sender else checked + sender
                                },
                            )
                            Text(sender)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmSenders(checked.toList()) },
                    enabled = checked.isNotEmpty(),
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPending) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
