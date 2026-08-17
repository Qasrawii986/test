package com.smsexpense.tracker.service.bubble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.smsexpense.tracker.R

/** Why the floating bubble can or cannot be shown right now. */
enum class BubbleBlocker {
    NONE,
    DISABLED_IN_SETTINGS,
    NO_OVERLAY_PERMISSION,
    START_NOT_ALLOWED,
    ;

    /** Localized explanation. Takes a Context because it also feeds notifications. */
    fun message(context: Context): String = when (this) {
        NONE -> ""
        DISABLED_IN_SETTINGS -> context.getString(R.string.blocker_disabled)
        NO_OVERLAY_PERMISSION -> context.getString(R.string.blocker_no_overlay)
        START_NOT_ALLOWED -> context.getString(R.string.blocker_start_not_allowed)
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
