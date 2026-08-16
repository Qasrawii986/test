package com.smsexpense.tracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onChooseFromSms: () -> Unit = {},
    onImportHistorical: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenQuickLaunch: () -> Unit = {},
    onDebugUnlocked: () -> Unit = {},
    versionLabel: String = "",
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Live permission status: makes a missing permission obvious
            // instead of the bubble just never appearing. ---
            PermissionStatusCard(lastBubbleStatus = state.lastBubbleStatus)

            // --- Bank sender IDs ---
            SectionCard(title = "Bank Sender IDs") {
                Text(
                    "Only messages from these senders are parsed. Add your bank's SMS sender name, e.g. MYBANK.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.senderIds.forEach { sender ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sender, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeSenderId(sender) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove $sender")
                        }
                    }
                }
                var newSender by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSender,
                        onValueChange = { newSender = it },
                        label = { Text("Sender ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.addSenderId(newSender)
                            newSender = ""
                        },
                        enabled = newSender.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add sender")
                    }
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onChooseFromSms,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose from SMS") }
            }

            // --- Historical import ---
            SectionCard(title = "Import") {
                Text(
                    "Scan bank SMS already on this device and import old payments. " +
                        "Already-imported messages are skipped automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onImportHistorical,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import Historical Transactions") }
            }

            // --- Currency ---
            SectionCard(title = "Currency") {
                var currency by remember(state.defaultCurrency) { mutableStateOf(state.defaultCurrency) }
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(4) },
                    label = { Text("Default currency (used when SMS has none)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currency != state.defaultCurrency && currency.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.setDefaultCurrency(currency) }) {
                        Text("Save currency")
                    }
                }
            }

            // --- Bubble ---
            SectionCard(title = "Bubble") {
                ToggleRow("Show floating bubble", state.bubble.enabled) {
                    viewModel.setBubbleEnabled(it)
                }
                Text(
                    "Auto-hide after ${state.bubble.autoHideSeconds}s (payment stays uncategorized)",
                    style = MaterialTheme.typography.bodySmall,
                )
                var autoHide by remember(state.bubble.autoHideSeconds) {
                    mutableFloatStateOf(state.bubble.autoHideSeconds.toFloat())
                }
                Slider(
                    value = autoHide,
                    onValueChange = { autoHide = it },
                    onValueChangeFinished = { viewModel.setBubbleAutoHide(autoHide.toInt()) },
                    valueRange = 10f..180f,
                )
            }

            // --- Bubble appearance ---
            SectionCard(title = "Bubble appearance") {
                BubbleAppearanceControls(
                    settings = state.bubble,
                    onSizeChange = viewModel::setBubbleSize,
                    onShapeChange = viewModel::setBubbleShape,
                    onColorChange = viewModel::setBubbleColor,
                    onOpacityChange = viewModel::setBubbleOpacity,
                    onShowAmountChange = viewModel::setBubbleShowAmount,
                )
            }

            // --- Parsing ---
            SectionCard(title = "Parsing") {
                Text(
                    "Confidence threshold: %.0f%%".format(state.confidenceThreshold * 100),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Messages scoring below this are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                )
                var threshold by remember(state.confidenceThreshold) {
                    mutableFloatStateOf(state.confidenceThreshold)
                }
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = { viewModel.setConfidenceThreshold(threshold) },
                    valueRange = 0f..1f,
                )
            }

            // --- Server ---
            SectionCard(title = "Server") {
                ToggleRow("Sync payments to server", state.api.enabled) {
                    viewModel.setApiEnabled(it)
                }
                var baseUrl by remember(state.api.baseUrl) { mutableStateOf(state.api.baseUrl) }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (e.g. https://myserver.com/api)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var token by remember(state.api.authToken) { mutableStateOf(state.api.authToken) }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Auth token (optional, sent as Bearer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (baseUrl != state.api.baseUrl || token != state.api.authToken) {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setApiBaseUrl(baseUrl)
                        viewModel.setApiAuthToken(token)
                    }) { Text("Save server settings") }
                }
            }

            // --- Quick launch ---
            SectionCard(title = "Quick launch") {
                Text(
                    "Open quick actions from anywhere: a Quick Settings tile, a home screen " +
                        "shortcut, a back tap, or the power button.",
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenQuickLaunch,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Set up quick launch") }
            }

            // --- Updates + about ---
            SectionCard(title = "About") {
                var taps by remember { mutableStateOf(0) }
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        taps++
                        if (taps >= DEBUG_UNLOCK_TAPS) {
                            taps = 0
                            onDebugUnlocked()
                        }
                    },
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check for updates") }
            }
            Spacer(Modifier)
        }
    }
}

/** Taps on the version label that reveal the developer screen in release builds. */
private const val DEBUG_UNLOCK_TAPS = 7

/**
 * Shows whether each permission the app depends on is currently granted, and
 * offers a one-tap fix. The overlay permission in particular is revoked every
 * time the app is reinstalled, which silently disables the bubble.
 */
@Composable
private fun PermissionStatusCard(lastBubbleStatus: String) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var permissions by remember {
        mutableStateOf(com.smsexpense.tracker.service.bubble.AppPermissions.read(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissions = com.smsexpense.tracker.service.bubble.AppPermissions.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { permissions = com.smsexpense.tracker.service.bubble.AppPermissions.read(context) }
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { permissions = com.smsexpense.tracker.service.bubble.AppPermissions.read(context) }

    SectionCard(title = "Permissions") {
        PermissionRow(
            label = "Read incoming SMS",
            granted = permissions.sms,
            onFix = { smsLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
        )
        PermissionRow(
            label = "Display over other apps (bubble)",
            granted = permissions.overlay,
            onFix = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )
        PermissionRow(
            label = "Notifications",
            granted = permissions.notifications,
            onFix = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        if (!permissions.overlay) {
            Text(
                "Without this the bubble cannot appear — payments are still saved and " +
                    "shown as a notification you can categorize.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (lastBubbleStatus.isNotBlank()) {
            Text(
                "Last payment: $lastBubbleStatus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (granted) "✅" else "⚠️")
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) {
            androidx.compose.material3.TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
