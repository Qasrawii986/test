package com.smsexpense.tracker.service.quicklaunch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import com.smsexpense.tracker.service.panel.QuickPanelService
import com.smsexpense.tracker.util.AppLog

/**
 * Transparent, UI-less activity that raises the quick panel and finishes. Every
 * foreground entry point (tile, home-screen shortcut, assistant) routes through
 * it, so there is exactly one place that decides what "open quickly" means.
 *
 * Also registered for ACTION_ASSIST: when the user picks this app as their
 * digital assistant, long-pressing the power button lands here.
 */
class QuickLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startForegroundService(Intent(this, QuickPanelService::class.java))
        }.onFailure { AppLog.e("Quick launch could not open the panel", it) }
        finish()
        overridePendingTransition(0, 0)
    }
}

/**
 * Quick Settings tile — the most reliable shortcut on every device: available
 * from any screen including the lock screen, with no battery cost.
 */
class QuickLaunchTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Collapsing the shade matters: it sits above overlay windows, so the
        // panel would otherwise be drawn behind it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
