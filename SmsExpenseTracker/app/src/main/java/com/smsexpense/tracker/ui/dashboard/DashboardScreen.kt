package com.smsexpense.tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.rootIdOf
import com.smsexpense.tracker.ui.components.CategoryBar
import com.smsexpense.tracker.ui.components.MonthPicker
import com.smsexpense.tracker.ui.components.PaymentRow
import com.smsexpense.tracker.ui.components.colorForCategory
import com.smsexpense.tracker.ui.components.formatAmount
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    defaultCurrency: String,
    onPaymentClick: (Long) -> Unit,
    onCategoriesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    debugVisible: Boolean,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCategoriesClick) {
                        Icon(Icons.Default.Category, contentDescription = stringResource(R.string.categories))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    if (debugVisible) {
                        IconButton(onClick = onDebugClick) {
                            Icon(Icons.Default.BugReport, contentDescription = stringResource(R.string.debug))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val stats = state.stats
        val currency = state.payments.firstOrNull()?.currency ?: defaultCurrency
        val categoriesById = state.categories.associateBy { it.id }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    MonthPicker(
                        selected = state.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Once expenses are shared, the headline is what the month
                        // actually cost *you*; the gross figure moves to the line
                        // underneath so neither number is lost.
                        Text(
                            if (state.hasSharing) {
                                stringResource(R.string.dashboard_your_share)
                            } else {
                                stringResource(R.string.dashboard_total_spending)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = formatAmount(
                                if (state.hasSharing) state.myShare else stats?.total ?: 0.0,
                                currency,
                            ),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.hasSharing) {
                            Text(
                                text = stringResource(
                                    R.string.dashboard_total_paid,
                                    formatAmount(stats?.total ?: 0.0, currency),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = stringResource(R.string.dashboard_transactions, stats?.transactionCount ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // What other people owe you from this month's expenses.
            val owedRows = state.owed.filter { it.amount > 0.0 }
            if (owedRows.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.dashboard_owed_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(owedRows, key = { "owed${it.payerId}" }) { owed ->
                    val payer = state.payers.find { it.id == owed.payerId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        R.string.payer_owes_you,
                                        payer?.let { "${it.emoji} ${it.name}" } ?: "👤",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    formatAmount(owed.amount, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.settle(owed.payerId) },
                            ) { Text(stringResource(R.string.payer_settle)) }
                        }
                    }
                }
            }

            if (state.uncategorized.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.dashboard_needs_categorizing, state.uncategorized.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(state.uncategorized, key = { "u${it.id}" }) { payment ->
                    PaymentRow(payment, categoriesById[payment.categoryId]) {
                        onPaymentClick(payment.id)
                    }
                }
            }

            // Report view: totals roll up into the main category, with the
            // subcategory breakdown listed underneath it.
            // With sharing on, the breakdown reports your own cost per category so
            // it adds up to the headline instead of contradicting it.
            val othersByCategory = state.chargedToOthersByCategory.associate { it.categoryId to it.total }
            val perCategory = stats?.perCategory.orEmpty()
                .map { row ->
                    if (!state.hasSharing) {
                        row
                    } else {
                        row.copy(
                            total = (row.total - (othersByCategory[row.categoryId] ?: 0.0))
                                .coerceAtLeast(0.0),
                        )
                    }
                }
                .filter { it.total > 0 }
            if (perCategory.isNotEmpty()) {
                val rootOf = state.categories.rootIdOf()
                val rollup = perCategory
                    .groupBy { row -> row.categoryId?.let { rootOf[it] } }
                    .mapValues { (_, rows) -> rows.sumOf { it.total } }
                    .entries.sortedByDescending { it.value }
                val maxRoot = rollup.maxOf { it.value }

                item {
                    Text(stringResource(R.string.dashboard_by_category), style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rollup.forEachIndexed { index, (rootId, rootTotal) ->
                                val root = rootId?.let { categoriesById[it] }
                                CategoryBar(
                                    label = root?.name ?: stringResource(R.string.uncategorized),
                                    icon = root?.icon ?: "❓",
                                    amount = rootTotal,
                                    currency = currency,
                                    fraction = (rootTotal / maxRoot).toFloat(),
                                    color = colorForCategory(root, index),
                                )
                                // Sub-breakdown, only when this root actually has splits.
                                val parts = perCategory
                                    .filter { row -> row.categoryId?.let { rootOf[it] } == rootId }
                                    .sortedByDescending { it.total }
                                if (parts.size > 1) {
                                    parts.forEach { part ->
                                        val leaf = part.categoryId?.let { categoriesById[it] }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 20.dp),
                                        ) {
                                            Text(
                                                "${leaf?.icon ?: "❓"} ${leaf?.name ?: stringResource(R.string.uncategorized)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                formatAmount(part.total, currency),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.payments.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.dashboard_latest), style = MaterialTheme.typography.titleMedium)
                }
                items(state.payments, key = { it.id }) { payment ->
                    PaymentRow(
                        payment = payment,
                        category = categoriesById[payment.categoryId],
                        onClick = { onPaymentClick(payment.id) },
                        shared = payment.id in state.sharedPaymentIds,
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.dashboard_empty), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dashboard_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
