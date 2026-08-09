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

private const val SAMPLE_AR = "تم خصم 12.50 JOD من بطاقتك لدى Coffee Shop"
private const val SAMPLE_EN = "Purchase of JOD 25.00 at SuperMart using card ending 1234"

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
                title = { Text("Developer Debug") },
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulate SMS", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        label = { Text("Sender ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message body") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { body = SAMPLE_AR }) { Text("Arabic sample") }
                        OutlinedButton(onClick = { body = SAMPLE_EN }) { Text("English sample") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = respectFilter, onCheckedChange = { respectFilter = it })
                        Text("Respect Sender ID filter", style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.simulateSms(sender, body, respectFilter) }) {
                            Text("Simulate SMS")
                        }
                        OutlinedButton(onClick = { viewModel.testParser(sender, body) }) {
                            Text("Test parser only")
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tools", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::triggerBubble) { Text("Trigger bubble") }
                        OutlinedButton(onClick = viewModel::createFakePayment) { Text("Fake payment") }
                        OutlinedButton(onClick = viewModel::testApi) { Text("Test API") }
                        OutlinedButton(onClick = viewModel::syncNow) { Text("Sync now") }
                        OutlinedButton(onClick = viewModel::clearDatabase) { Text("Clear DB") }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Log", style = MaterialTheme.typography.titleMedium)
                    if (log.isEmpty()) {
                        Text("No events yet", style = MaterialTheme.typography.bodySmall)
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
