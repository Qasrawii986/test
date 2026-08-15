package com.smsexpense.tracker.service.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.smsexpense.tracker.util.AppLog

/**
 * Receives the outcome of an install session. The important case is
 * STATUS_PENDING_USER_ACTION: the platform hands back an intent that shows the
 * install confirmation UI, which we must launch ourselves.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                        .onFailure { AppLog.e("Could not show install confirmation", it) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> AppLog.d("Update installed")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppLog.e("Install session failed (status=$status): $message")
                lastError = message ?: "Install failed with status $status"
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.smsexpense.tracker.INSTALL_RESULT"

        /** Surfaced by the update screen when the session reports a failure. */
        @Volatile
        var lastError: String? = null
    }
}
