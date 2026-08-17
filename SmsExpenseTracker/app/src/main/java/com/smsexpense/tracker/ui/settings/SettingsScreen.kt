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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onChooseFromSms: () -> Unit = {},
    onImportHistorical: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenQuickLaunch: () -> Unit = {},
    onOpenNotificationSource: () -> Unit = {},
    onOpenPayers: () -> Unit = {},
    onDebugUnlocked: () -> Unit = {},
    versionLabel: String = "",
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Language ---
            SectionCard(title = stringResource(R.string.settings_language)) {
                val activity = LocalContext.current as? android.app.Activity
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val labels = mapOf(
                        com.smsexpense.tracker.util.AppLocale.SYSTEM to R.string.settings_language_system,
                        com.smsexpense.tracker.util.AppLocale.ENGLISH to R.string.settings_language_en,
                        com.smsexpense.tracker.util.AppLocale.ARABIC to R.string.settings_language_ar,
                    )
                    com.smsexpense.tracker.util.AppLocale.SUPPORTED.forEach { code ->
                        androidx.compose.material3.FilterChip(
                            selected = state.language == code,
                            onClick = {
                                // Recreating rebuilds every screen against the new locale,
                                // including the layout direction.
                                viewModel.setLanguage(code) { activity?.recreate() }
                            },
                            label = { Text(stringResource(labels.getValue(code))) },
                        )
                    }
                }
            }

            // --- Live permission status: makes a missing permission obvious
            // instead of the bubble just never appearing. ---
            PermissionStatusCard(lastBubbleStatus = state.lastBubbleStatus)

            // --- Bank sender IDs ---
            SectionCard(title = stringResource(R.string.settings_sender_ids)) {
                Text(
                    stringResource(R.string.settings_sender_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.senderIds.forEach { sender ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sender, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeSenderId(sender) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_remove_sender, sender))
                        }
                    }
                }
                var newSender by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSender,
                        onValueChange = { newSender = it },
                        label = { Text(stringResource(R.string.settings_sender_id)) },
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
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_sender))
                    }
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onChooseFromSms,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.setup_choose_from_sms)) }
            }

            // --- Notification source ---
            SectionCard(title = stringResource(R.string.settings_notifications_source)) {
                Text(
                    stringResource(R.string.settings_notifications_source_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenNotificationSource,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_choose_apps)) }
            }

            // --- Historical import ---
            SectionCard(title = stringResource(R.string.settings_import)) {
                Text(
                    stringResource(R.string.settings_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onImportHistorical,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_import_button)) }
            }

            // --- Expense sharing ---
            SectionCard(title = stringResource(R.string.settings_payers)) {
                Text(
                    stringResource(R.string.settings_payers_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenPayers,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.payers_title)) }
            }

            // --- Currency ---
            SectionCard(title = stringResource(R.string.settings_currency)) {
                var currency by remember(state.defaultCurrency) { mutableStateOf(state.defaultCurrency) }
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(4) },
                    label = { Text(stringResource(R.string.settings_currency_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currency != state.defaultCurrency && currency.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.setDefaultCurrency(currency) }) {
                        Text(stringResource(R.string.settings_save_currency))
                    }
                }
            }

            // --- Bubble ---
            SectionCard(title = stringResource(R.string.settings_bubble)) {
                ToggleRow(stringResource(R.string.settings_bubble_show), state.bubble.enabled) {
                    viewModel.setBubbleEnabled(it)
                }
                // Auto-hide: any duration up to 10 minutes, or off entirely.
                // "Off" keeps a foreground service and an overlay alive until you
                // act, so the trade-off is spelled out rather than hidden.
                ToggleRow(
                    stringResource(R.string.settings_autohide_never),
                    state.bubble.autoHideDisabled,
                ) { never ->
                    viewModel.setBubbleAutoHide(
                        if (never) BubbleSettings.NEVER_AUTO_HIDE else DEFAULT_AUTO_HIDE_SECONDS
                    )
                }
                if (state.bubble.autoHideDisabled) {
                    Text(
                        stringResource(R.string.settings_autohide_never_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    var autoHide by remember(state.bubble.autoHideSeconds) {
                        mutableFloatStateOf(state.bubble.autoHideSeconds.toFloat())
                    }
                    Text(
                        stringResource(R.string.settings_autohide, autoHide.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = autoHide,
                        onValueChange = { autoHide = it },
                        onValueChangeFinished = { viewModel.setBubbleAutoHide(autoHide.toInt()) },
                        valueRange = BubbleSettings.MIN_AUTO_HIDE_SECONDS.toFloat()..
                            BubbleSettings.MAX_AUTO_HIDE_SECONDS.toFloat(),
                    )
                }
            }

            // --- Bubble position ---
            SectionCard(title = stringResource(R.string.settings_bubble_position)) {
                BubblePositionPicker(
                    settings = state.bubble,
                    onPositionChange = viewModel::setBubblePosition,
                    onRememberChange = viewModel::setBubbleRememberPosition,
                    onSnapChange = viewModel::setBubbleSnapToEdge,
                )
            }

            // --- Bubble appearance ---
            SectionCard(title = stringResource(R.string.settings_bubble_appearance)) {
                BubbleAppearanceControls(
                    settings = state.bubble,
                    onSizeChange = viewModel::setBubbleSize,
                    onShapeChange = viewModel::setBubbleShape,
                    onColorChange = viewModel::setBubbleColor,
                    onOpacityChange = viewModel::setBubbleOpacity,
                    onShowAmountChange = viewModel::setBubbleShowAmount,
                    onBackgroundPicked = viewModel::setBubbleBackgroundFrom,
                    onBackgroundCleared = viewModel::clearBubbleBackground,
                )
            }

            // --- Parsing ---
            SectionCard(title = stringResource(R.string.settings_parsing)) {
                Text(
                    stringResource(R.string.settings_threshold, (state.confidenceThreshold * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.settings_threshold_hint),
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
            SectionCard(title = stringResource(R.string.settings_server)) {
                ToggleRow(stringResource(R.string.settings_sync_toggle), state.api.enabled) {
                    viewModel.setApiEnabled(it)
                }
                var baseUrl by remember(state.api.baseUrl) { mutableStateOf(state.api.baseUrl) }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.settings_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var token by remember(state.api.authToken) { mutableStateOf(state.api.authToken) }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.settings_auth_token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (baseUrl != state.api.baseUrl || token != state.api.authToken) {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setApiBaseUrl(baseUrl)
                        viewModel.setApiAuthToken(token)
                    }) { Text(stringResource(R.string.settings_save_server)) }
                }
            }

            // --- Quick launch ---
            SectionCard(title = stringResource(R.string.settings_quick_launch)) {
                Text(
                    stringResource(R.string.settings_quick_launch_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenQuickLaunch,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_quick_launch_button)) }
            }

            // --- Updates + about ---
            SectionCard(title = stringResource(R.string.settings_about)) {
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
                ) { Text(stringResource(R.string.settings_check_updates)) }
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

    SectionCard(title = stringResource(R.string.settings_permissions)) {
        PermissionRow(
            label = stringResource(R.string.settings_perm_sms),
            granted = permissions.sms,
            onFix = { smsLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
        )
        PermissionRow(
            label = stringResource(R.string.settings_perm_overlay),
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
            label = stringResource(R.string.settings_perm_notifications),
            granted = permissions.notifications,
            onFix = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        if (!permissions.overlay) {
            Text(
                stringResource(R.string.settings_overlay_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (lastBubbleStatus.isNotBlank()) {
            Text(
                stringResource(R.string.settings_last_payment, lastBubbleStatus),
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
            androidx.compose.material3.TextButton(onClick = onFix) { Text(stringResource(R.string.fix)) }
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

/** Restored when auto-hide is switched back on. */
private const val DEFAULT_AUTO_HIDE_SECONDS = 45
