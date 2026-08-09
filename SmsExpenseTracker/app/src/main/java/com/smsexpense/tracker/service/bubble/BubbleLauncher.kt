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
 * SYSTEM_ALERT_WINDOW is itself a documented exemption. We still catch
 * ForegroundServiceStartNotAllowedException defensively — if the start is denied,
 * the payment simply stays UNCATEGORIZED and appears in the app.
 */
object BubbleLauncher {

    suspend fun launchIfPossible(context: Context, paymentId: Long) {
        val settings = context.appContainer().settingsRepository.bubbleSettings.first()
        if (!settings.enabled) {
            AppLog.d("Bubble disabled in settings; payment stays uncategorized")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            AppLog.d("No overlay permission; payment stays uncategorized")
            return
        }
        val intent = Intent(context, BubbleService::class.java)
            .putExtra(BubbleService.EXTRA_PAYMENT_ID, paymentId)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                AppLog.w("FGS start not allowed; payment stays uncategorized", e)
            } else {
                AppLog.e("Failed to start BubbleService", e)
            }
        }
    }
}
