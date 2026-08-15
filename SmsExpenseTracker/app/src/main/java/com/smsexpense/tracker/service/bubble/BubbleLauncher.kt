package com.smsexpense.tracker.service.bubble

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.flow.first

/**
 * Starts the bubble foreground service when allowed. Starting an FGS from the
 * background is legal here on Android 12+ for two independent reasons:
 * the SMS broadcast puts the app on the temporary allowlist, and holding
 * SYSTEM_ALERT_WINDOW is itself a documented exemption.
 *
 * Returns the reason the bubble could not be shown so callers can fall back to
 * a notification instead of failing silently.
 */
object BubbleLauncher {

    suspend fun launchIfPossible(context: Context, paymentId: Long): BubbleBlocker {
        val settings = context.appContainer().settingsRepository.bubbleSettings.first()
        if (!settings.enabled) {
            AppLog.d("Bubble disabled in settings; payment stays uncategorized")
            return BubbleBlocker.DISABLED_IN_SETTINGS
        }
        if (!Settings.canDrawOverlays(context)) {
            AppLog.d("No overlay permission; falling back to notification")
            return BubbleBlocker.NO_OVERLAY_PERMISSION
        }
        val intent = Intent(context, BubbleService::class.java)
            .putExtra(BubbleService.EXTRA_PAYMENT_ID, paymentId)
        return try {
            context.startForegroundService(intent)
            BubbleBlocker.NONE
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                AppLog.w("FGS start not allowed; falling back to notification", e)
            } else {
                AppLog.e("Failed to start BubbleService", e)
            }
            BubbleBlocker.START_NOT_ALLOWED
        }
    }
}
