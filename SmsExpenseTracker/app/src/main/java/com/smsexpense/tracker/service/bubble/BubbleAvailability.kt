package com.smsexpense.tracker.service.bubble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Why the floating bubble can or cannot be shown right now. */
enum class BubbleBlocker {
    NONE,
    DISABLED_IN_SETTINGS,
    NO_OVERLAY_PERMISSION,
    START_NOT_ALLOWED,
    ;

    val userMessage: String
        get() = when (this) {
            NONE -> ""
            DISABLED_IN_SETTINGS -> "The floating bubble is turned off in Settings."
            NO_OVERLAY_PERMISSION ->
                "Grant \"display over other apps\" to get the one-tap bubble. " +
                    "This permission resets whenever the app is reinstalled."
            START_NOT_ALLOWED -> "Android blocked the bubble from starting in the background."
        }
}

/** Runtime permission snapshot, surfaced in Settings so nothing fails silently. */
data class AppPermissions(
    val sms: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
) {
    companion object {
        fun read(context: Context) = AppPermissions(
            sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED,
            overlay = Settings.canDrawOverlays(context),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}
