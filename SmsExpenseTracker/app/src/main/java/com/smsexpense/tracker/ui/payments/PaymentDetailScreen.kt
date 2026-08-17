package com.smsexpense.tracker.ui.payments

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.components.formatDate
import com.smsexpense.tracker.ui.components.formatTime
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentDetailScreen(
    viewModel: PaymentDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var editingDetails by remember { mutableStateOf(false) }
    var editingSplit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.delete(onDone = onBack) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        val payment = state.payment
        if (payment == null) {
            if (!state.loading) {
                Column(Modifier.padding(padding).padding(16.dp)) { Text(stringResource(R.string.payment_not_found)) }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatAmount(payment.amount, payment.currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    payment.merchant?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = { editingDetails = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_details),
                    )
                }
            }

            // Who carries this expense. Shown before the category because a
            // reimbursed expense is often categorized differently.
            val split = state.split
            if (state.payers.isNotEmpty() && split != null) {
                Text(stringResource(R.string.split_who_pays), style = MaterialTheme.typography.titleMedium)
                com.smsexpense.tracker.ui.components.PayerChips(
                    payers = state.payers,
                    split = split,
                    onChargeWholeTo = { viewModel.chargeWholeTo(it) },
                    onSplitClick = { editingSplit = true },
                )
                if (!split.isFullyMine) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            com.smsexpense.tracker.ui.components.SplitSummary(
                                payers = state.payers,
                                split = split,
                            )
                            split.allocations.forEach { allocation ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            viewModel.setAllocationSettled(
                                                allocation.id, !allocation.settled,
                                            )
                                        },
                                    ) {
                                        Text(
                                            if (allocation.settled) {
                                                stringResource(R.string.payer_reopen)
                                            } else {
                                                stringResource(R.string.payer_settle)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (editingSplit) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        com.smsexpense.tracker.ui.components.SplitEditor(
                            payers = state.payers,
                            split = split,
                            onSave = {
                                viewModel.saveSplit(it)
                                editingSplit = false
                            },
                            onCancel = { editingSplit = false },
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            DetailRow(stringResource(R.string.payment_date), formatDate(payment.timestamp))
            DetailRow(stringResource(R.string.payment_time), formatTime(payment.timestamp))
            DetailRow(stringResource(R.string.payment_sender), payment.sender)
            DetailRow(stringResource(R.string.payment_status), payment.status.name)
            DetailRow(stringResource(R.string.payment_sync), payment.syncStatus.name)
            DetailRow(stringResource(R.string.payment_confidence), "%.0f%%".format(payment.confidence * 100))

            Text(stringResource(R.string.payment_category), style = MaterialTheme.typography.titleMedium)
            com.smsexpense.tracker.ui.components.categoryPath(state.categories, payment.categoryId)
                ?.let { path ->
                    Text(
                        stringResource(R.string.payment_current_category, path),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            com.smsexpense.tracker.ui.components.CategoryChips(
                categories = state.categories,
                onSelected = { viewModel.setCategory(it) },
                selectedId = payment.categoryId,
            )

            Text(stringResource(R.string.payment_original_sms), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = payment.originalMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (editingDetails) {
            EditDetailsDialog(
                initialMerchant = payment.merchant.orEmpty(),
                initialAmount = com.smsexpense.tracker.ui.components.trimAmount(payment.amount),
                onConfirm = { merchant, amount ->
                    viewModel.updateDetails(merchant, amount)
                    editingDetails = false
                },
                onDismiss = { editingDetails = false },
            )
        }
    }
}

@Composable
private fun EditDetailsDialog(
    initialMerchant: String,
    initialAmount: String,
    onConfirm: (String?, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var merchant by remember { mutableStateOf(initialMerchant) }
    var amount by remember { mutableStateOf(initialAmount) }
    val parsed = amount.trim().toDoubleOrNull()
    val valid = parsed != null && parsed > 0.0

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.edit_merchant)) },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = amount,
                    onValueChange = { text -> amount = text.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.edit_amount)) },
                    singleLine = true,
                    isError = amount.isNotEmpty() && !valid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                )
                if (amount.isNotEmpty() && !valid) {
                    Text(
                        stringResource(R.string.edit_amount_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(merchant.trim().ifEmpty { null }, parsed ?: 0.0) },
                enabled = valid,
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_cancel))
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.35f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
