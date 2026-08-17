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
            Text(
                text = formatAmount(payment.amount, payment.currency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            payment.merchant?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
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
    }
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
