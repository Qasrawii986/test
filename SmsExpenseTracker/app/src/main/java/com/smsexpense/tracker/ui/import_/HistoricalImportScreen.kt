package com.smsexpense.tracker.ui.import_

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.toTree
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.components.formatDate
import com.smsexpense.tracker.ui.components.formatDateTime
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoricalImportScreen(
    viewModel: HistoricalImportViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // True immersive full screen for this screen only: the system navigation bar
    // is hidden while this composable is shown and restored on exit.
    com.smsexpense.tracker.ui.components.ImmersiveEffect()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        viewModel.onPermissionResult(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step is ImportStep.Review) viewModel.backToSetup() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val review = state.step as? ImportStep.Review
            if (review != null && review.items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            stringResource(R.string.import_summary, review.selectedCount, formatAmount(review.selectedTotal, review.currency)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var showBulkDialog by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showBulkDialog = true },
                                enabled = review.selectedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.import_set_category)) }
                            Button(
                                onClick = viewModel::requestImport,
                                enabled = review.selectedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.import_selected_button)) }
                            if (showBulkDialog) {
                                CategoryPickerDialog(
                                    categories = state.categories,
                                    title = stringResource(R.string.import_category_for, review.selectedCount),
                                    onPick = {
                                        viewModel.setCategoryForSelected(it)
                                        showBulkDialog = false
                                    },
                                    onDismiss = { showBulkDialog = false },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val step = state.step) {
            ImportStep.Setup -> SetupContent(
                state = state,
                viewModel = viewModel,
                onGrantPermission = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                modifier = Modifier.padding(padding),
            )
            is ImportStep.Scanning -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.import_scanning), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.import_scanned, step.messagesScanned),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.import_found, step.transactionsFound),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is ImportStep.Review -> ReviewContent(
                review = step,
                categories = state.categories,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            ImportStep.Importing -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.import_importing))
            }
            is ImportStep.Done -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✅", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_imported, step.summary.imported),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.import_total, formatAmount(step.summary.total, step.summary.currency)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (step.summary.duplicatesSkipped > 0) {
                    Text(
                        stringResource(R.string.import_duplicates, step.summary.duplicatesSkipped),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.done)) }
            }
        }
    }

    if (state.showConfirmDialog) {
        val review = state.step as? ImportStep.Review
        if (review != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissConfirm,
                title = { Text(stringResource(R.string.import_confirm_title)) },
                text = {
                    Text(
                        stringResource(R.string.import_confirm_body, review.selectedCount, formatAmount(review.selectedTotal, review.currency)),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.import_yes_import)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetupContent(
    state: HistoricalImportUiState,
    viewModel: HistoricalImportViewModel,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.permissionGranted) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_needs_sms), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.import_needs_sms_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onGrantPermission) { Text(stringResource(R.string.picker_grant)) }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_bank), style = MaterialTheme.typography.titleMedium)
                if (state.configuredSenders.isEmpty()) {
                    Text(
                        stringResource(R.string.import_no_senders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.configuredSenders.forEach { sender ->
                            FilterChip(
                                selected = sender in state.selectedSenders,
                                onClick = { viewModel.toggleSender(sender) },
                                label = { Text(sender) },
                            )
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_period), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { viewModel.setPresetMonths(1) }, label = { Text(stringResource(R.string.import_last_month)) })
                    AssistChip(onClick = { viewModel.setPresetMonths(3) }, label = { Text(stringResource(R.string.import_3_months)) })
                    AssistChip(onClick = { viewModel.setPresetMonths(6) }, label = { Text(stringResource(R.string.import_6_months)) })
                    AssistChip(onClick = { viewModel.setPresetMonths(12) }, label = { Text(stringResource(R.string.import_1_year)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        label = stringResource(R.string.import_from),
                        millis = state.fromDate,
                        onPicked = viewModel::setFromDate,
                        modifier = Modifier.weight(1f),
                    )
                    DateField(
                        label = stringResource(R.string.import_to),
                        millis = state.toDate,
                        onPicked = viewModel::setToDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Button(
            onClick = viewModel::scan,
            enabled = state.permissionGranted && state.selectedSenders.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_scan)) }

        if (state.history.isNotEmpty()) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_history), style = MaterialTheme.typography.titleMedium)
                    state.history.forEach { record ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                formatDateTime(record.importedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${formatDate(record.fromDate)} → ${formatDate(record.toDate)}: " +
                                    "${record.transactionCount} transactions, " +
                                    formatAmount(record.totalAmount, record.currency),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    millis: Long,
    onPicked: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text("$label: ${formatDate(millis)}")
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onPicked)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ReviewContent(
    review: ImportStep.Review,
    categories: List<Category>,
    viewModel: HistoricalImportViewModel,
    modifier: Modifier = Modifier,
) {
    if (review.items.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.import_none_new), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (review.alreadyImported > 0) stringResource(R.string.import_scanned_with_imported, review.scanned, review.alreadyImported)
                else stringResource(R.string.import_scanned_summary, review.scanned),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                if (review.alreadyImported > 0) stringResource(R.string.import_found_with_imported, review.items.size, review.alreadyImported)
                else stringResource(R.string.import_found_count, review.items.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::toggleSelectAll) {
                Text(if (review.allSelected) stringResource(R.string.import_deselect_all) else stringResource(R.string.import_select_all))
            }
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 16.dp, bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(review.items) { index, item ->
                var showCategoryDialog by remember { mutableStateOf(false) }
                val category = categories.find { it.id == item.categoryId }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleItem(index) },
                ) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { viewModel.toggleItem(index) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.candidate.merchant ?: item.candidate.sender,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            formatDateTime(item.candidate.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AssistChip(
                            onClick = { showCategoryDialog = true },
                            label = {
                                Text(
                                    category?.let { "${it.icon} ${it.name}" } ?: ("❓ " + stringResource(R.string.uncategorized))
                                )
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatAmount(item.candidate.amount, item.candidate.currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (showCategoryDialog) {
                    CategoryPickerDialog(
                        categories = categories,
                        title = stringResource(R.string.payment_category),
                        onPick = {
                            viewModel.setItemCategory(index, it)
                            showCategoryDialog = false
                        },
                        onDismiss = { showCategoryDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    title: String,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    ("❓ " + stringResource(R.string.uncategorized)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 10.dp),
                )
                // Roots followed by their indented subcategories.
                categories.toTree().forEach { node ->
                    Text(
                        "${node.category.icon} ${node.category.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(node.category.id) }
                            .padding(vertical = 10.dp),
                    )
                    node.children.forEach { child ->
                        Text(
                            "${child.icon} ${child.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(child.id) }
                                .padding(start = 24.dp)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
