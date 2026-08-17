package com.smsexpense.tracker.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

private const val SAMPLE_AR = "تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop"
private const val SAMPLE_EN = "Purchase of JOD 25.00 at SuperMart using card ending 1234"
private const val SAMPLE_CLIQ = "13.000 JOD CliQ transfer to Abdulraheem Rizk.\nAvailable balance: 594.511 JOD."

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel,
    onBack: () -> Unit,
) {
    val log by viewModel.log.collectAsState()
    var sender by remember { mutableStateOf("MYBANK") }
    var body by remember { mutableStateOf(SAMPLE_AR) }
    var respectFilter by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.debug_simulate), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        label = { Text(stringResource(R.string.settings_sender_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(R.string.debug_message_body)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { body = SAMPLE_AR }) { Text(stringResource(R.string.debug_arabic_sample)) }
                        OutlinedButton(onClick = { body = SAMPLE_EN }) { Text(stringResource(R.string.debug_english_sample)) }
                        OutlinedButton(onClick = { body = SAMPLE_CLIQ }) { Text(stringResource(R.string.debug_cliq_sample)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = respectFilter, onCheckedChange = { respectFilter = it })
                        Text(stringResource(R.string.debug_respect_filter), style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.simulateSms(sender, body, respectFilter) }) {
                            Text(stringResource(R.string.debug_simulate))
                        }
                        OutlinedButton(onClick = { viewModel.testParser(sender, body) }) {
                            Text(stringResource(R.string.debug_test_parser))
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.debug_tools), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::triggerBubble) { Text(stringResource(R.string.debug_trigger_bubble)) }
                        OutlinedButton(onClick = viewModel::createFakePayment) { Text(stringResource(R.string.debug_fake_payment)) }
                        OutlinedButton(onClick = viewModel::testApi) { Text(stringResource(R.string.debug_test_api)) }
                        OutlinedButton(onClick = viewModel::syncNow) { Text(stringResource(R.string.debug_sync_now)) }
                        OutlinedButton(onClick = viewModel::clearDatabase) { Text(stringResource(R.string.debug_clear_db)) }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.debug_log), style = MaterialTheme.typography.titleMedium)
                    if (log.isEmpty()) {
                        Text(stringResource(R.string.debug_no_events), style = MaterialTheme.typography.bodySmall)
                    }
                    log.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
