package com.smsexpense.tracker.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smsexpense.tracker.service.update.ApkInstaller
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Coming back from the system settings / install dialog: re-read the
    // permission and surface any failure the install session reported.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInstallPermission()
                viewModel.consumeSessionError()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.updates)) },
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.update_installed_version), style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${state.currentVersionName} (build ${state.currentVersionCode})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!state.canInstall) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_permission_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.update_permission_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = {
                            context.startActivity(ApkInstaller.unknownSourcesIntent(context))
                        }) { Text(stringResource(R.string.update_permission_button)) }
                    }
                }
            }

            when (val stage = state.stage) {
                UpdateStage.Idle -> {
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_check_updates))
                    }
                }
                UpdateStage.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        Text(stringResource(R.string.update_checking))
                    }
                }
                is UpdateStage.UpToDate -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.update_latest, stage.versionName),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_check_again))
                    }
                }
                is UpdateStage.Available -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.update_available, stage.info.versionName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (stage.info.notes.isNotBlank()) {
                                Text(stage.info.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Button(
                        onClick = viewModel::download,
                        enabled = state.canInstall,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.update_download)) }
                }
                is UpdateStage.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (stage.progress * 100).toInt()))
                    LinearProgressIndicator(
                        progress = { stage.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is UpdateStage.ReadyToInstall -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.update_ready, stage.info.versionName),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.update_ready_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Button(onClick = viewModel::install, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_install_now))
                    }
                }
                is UpdateStage.Installing -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Spacer(Modifier.padding(horizontal = 8.dp))
                                Text(
                                    stringResource(R.string.update_installing, stage.info.versionName),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text(
                                stringResource(R.string.update_installing_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::installViaSystemInstaller,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.update_nothing_appeared)) }
                }
                is UpdateStage.Installed -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.update_success, stage.versionName),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.update_success_body),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_check_updates))
                    }
                }
                is UpdateStage.Error -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.update_error), style = MaterialTheme.typography.titleMedium)
                            Text(stage.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (viewModel.hasDownloadedApk()) {
                        Button(
                            onClick = viewModel::installViaSystemInstaller,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.update_system_installer)) }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.try_again))
                    }
                }
            }
        }
    }
}
