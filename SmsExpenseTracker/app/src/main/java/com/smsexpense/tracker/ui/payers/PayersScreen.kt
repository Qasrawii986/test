package com.smsexpense.tracker.ui.payers

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.R
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.components.payerLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayersScreen(
    viewModel: PayersViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Payer?>(null) }
    var deleting by remember { mutableStateOf<Pair<Payer, Int>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.payers_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.payers_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.rows.none { !it.payer.isSelf } && !state.loading) {
                item {
                    Text(
                        stringResource(R.string.payers_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.rows, key = { it.payer.id }) { row ->
                PayerCard(
                    row = row,
                    currency = state.currency,
                    onEdit = { editing = row.payer },
                    onDelete = {
                        scope.launch {
                            deleting = row.payer to viewModel.chargedCount(row.payer.id)
                        }
                    },
                    onSettle = { viewModel.settle(row.payer.id) },
                    onReopen = { viewModel.reopen(row.payer.id) },
                )
            }
        }
    }

    if (showAdd) {
        PayerDialog(
            title = stringResource(R.string.payers_add),
            initialName = "",
            initialEmoji = "",
            onConfirm = { name, emoji ->
                viewModel.add(name, emoji)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { payer ->
        PayerDialog(
            title = stringResource(R.string.payers_edit),
            initialName = payer.name,
            initialEmoji = payer.emoji,
            onConfirm = { name, emoji ->
                viewModel.update(payer, name, emoji)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { (payer, chargedCount) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.payer_delete_title, payer.name)) },
            text = {
                Text(
                    if (chargedCount > 0) {
                        stringResource(R.string.payer_delete_message, chargedCount)
                    } else {
                        stringResource(R.string.payer_delete_message_empty)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(payer.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PayerCard(
    row: PayerRow,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit,
    onReopen: () -> Unit,
) {
    val owed = row.outstanding > 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (owed) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.payer.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        payerLabel(row.payer),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row.payer.isSelf) {
                        Text(
                            stringResource(R.string.payers_self_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!row.payer.isSelf) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
            if (!row.payer.isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (owed) {
                                stringResource(R.string.payer_owes_you, row.payer.name)
                            } else {
                                stringResource(R.string.payer_all_settled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (owed) {
                            Text(
                                formatAmount(row.outstanding, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.payer_owed_count, row.outstandingCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (owed) {
                        TextButton(onClick = onSettle) {
                            Text(stringResource(R.string.payer_settle))
                        }
                    } else {
                        TextButton(onClick = onReopen) {
                            Text(stringResource(R.string.payer_reopen))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayerDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.payer_name)) },
                    placeholder = { Text(stringResource(R.string.payer_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text(stringResource(R.string.payer_emoji)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, emoji) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
