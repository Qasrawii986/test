package com.smsexpense.tracker.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PermissionsState(
    val smsGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    val allGranted: Boolean get() = smsGranted && overlayGranted && notificationsGranted
}

/**
 * Step-by-step permission onboarding: each permission is requested individually
 * with an explanation of why it's needed. The user can skip and grant later.
 */
@Composable
fun PermissionsScreen(
    state: PermissionsState,
    onRefresh: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Welcome 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "SMS Expense reads your bank's payment SMS and lets you categorize each expense with one tap. It needs three permissions to work:",
            style = MaterialTheme.typography.bodyLarge,
        )

        PermissionCard(
            step = "1",
            title = "SMS access",
            explanation = "Required to detect payment messages the moment your bank sends them. Messages are processed on-device only.",
            granted = state.smsGranted,
            buttonText = "Grant SMS permission",
            onClick = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) },
        )

        PermissionCard(
            step = "2",
            title = "Display over other apps",
            explanation = "Required to show the small floating bubble on top of whatever app is open, so you can categorize a payment without switching apps.",
            granted = state.overlayGranted,
            buttonText = "Open overlay settings",
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )

        PermissionCard(
            step = "3",
            title = "Notifications",
            explanation = "Required on Android 13+ so the short-lived service behind the bubble can show its status notification.",
            granted = state.notificationsGranted,
            buttonText = "Grant notifications",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onRefresh()
                }
            },
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) { Text(if (state.allGranted) "Continue" else "Skip for now") }
        }
    }
}

@Composable
private fun PermissionCard(
    step: String,
    title: String,
    explanation: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (granted) "✅" else step,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}
