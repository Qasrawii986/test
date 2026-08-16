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
                title = { Text("Updates") },
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Installed version", style = MaterialTheme.typography.labelMedium)
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
                        Text("Permission needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "To install updates itself, this app needs the \"install unknown " +
                                "apps\" permission. You only grant it once.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = {
                            context.startActivity(ApkInstaller.unknownSourcesIntent(context))
                        }) { Text("Open permission settings") }
                    }
                }
            }

            when (val stage = state.stage) {
                UpdateStage.Idle -> {
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Check for updates")
                    }
                }
                UpdateStage.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        Text("Checking…")
                    }
                }
                is UpdateStage.UpToDate -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("✅ You're up to date", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Version ${stage.versionName} is the latest release.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Check again")
                    }
                }
                is UpdateStage.Available -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Version ${stage.info.versionName} available",
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
                    ) { Text("Download update") }
                }
                is UpdateStage.Downloading -> {
                    Text("Downloading… ${(stage.progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { stage.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is UpdateStage.ReadyToInstall -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Ready to install ${stage.info.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Android will show its own confirmation screen. The app closes " +
                                    "while it installs, then you reopen it — your data is kept.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Button(onClick = viewModel::install, modifier = Modifier.fillMaxWidth()) {
                        Text("Install now")
                    }
                }
                is UpdateStage.Installing -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Spacer(Modifier.padding(horizontal = 8.dp))
                                Text(
                                    "Installing ${stage.info.versionName}…",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text(
                                "Confirm on Android's install screen. This app will close while " +
                                    "it is replaced — reopen it afterwards and this screen will " +
                                    "confirm the new version.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::installViaSystemInstaller,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Nothing appeared — try again") }
                }
                is UpdateStage.Installed -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "✅ Updated to ${stage.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "The update installed successfully.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Check for updates")
                    }
                }
                is UpdateStage.Error -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                            Text(stage.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (viewModel.hasDownloadedApk()) {
                        Button(
                            onClick = viewModel::installViaSystemInstaller,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Install with system installer") }
                    }
                    OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
