package com.smsexpense.tracker.service.panel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smsexpense.tracker.AppContainer
import com.smsexpense.tracker.MainActivity
import com.smsexpense.tracker.R
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.service.bubble.OverlayViewOwner
import com.smsexpense.tracker.service.notification.PaymentNotifier
import com.smsexpense.tracker.service.sync.SyncScheduler
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The destination of every quick-launch method: a lightweight overlay panel with
 * this month's total and any payments still waiting to be categorized, so the
 * common task is done without opening the app at all.
 *
 * Rendering an overlay (rather than starting an activity) is also what keeps this
 * legal from a background trigger — background *activity* starts are restricted,
 * drawing an overlay with SYSTEM_ALERT_WINDOW is not.
 */
class QuickPanelService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var viewOwner: OverlayViewOwner? = null

    private val monthTotal = MutableStateFlow(0.0)
    private val currency = MutableStateFlow("JOD")
    private val uncategorized = MutableStateFlow<List<Payment>>(emptyList())
    private val categories = MutableStateFlow<List<Category>>(emptyList())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = appContainer()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()

        val now = YearMonth.now()
        scope.launch {
            container.paymentRepository.observeMonthlyStats(now.year, now.monthValue)
                .collect { monthTotal.value = it.total }
        }
        scope.launch {
            container.paymentRepository.observeUncategorized().collect { uncategorized.value = it }
        }
        scope.launch {
            container.categoryRepository.observeAll().collect { categories.value = it }
        }
        scope.launch { currency.value = container.settingsRepository.defaultCurrency.first() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!Settings.canDrawOverlays(this)) {
            AppLog.w("Quick panel needs the overlay permission")
            stopSelf()
            return START_NOT_STICKY
        }
        showPanel()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            AppLog.e("Quick panel could not go foreground", e)
            stopSelf()
        }
    }

    private fun showPanel() {
        if (overlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        val owner = OverlayViewOwner().also { it.create() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                QuickPanel(
                    monthTotalFlow = monthTotal,
                    currencyFlow = currency,
                    uncategorizedFlow = uncategorized,
                    categoriesFlow = categories,
                    onCategorize = ::categorize,
                    onOpenApp = ::openApp,
                    onClose = { stopSelf() },
                )
            }
        }
        try {
            windowManager?.addView(view, params)
            overlayView = view
            viewOwner = owner
        } catch (e: Exception) {
            AppLog.e("Could not show quick panel", e)
            owner.destroy()
            stopSelf()
        }
    }

    private fun categorize(paymentId: Long, categoryId: Long) {
        scope.launch {
            runCatching {
                container.paymentRepository.categorize(paymentId, categoryId)
                PaymentNotifier.cancel(this@QuickPanelService, paymentId)
                SyncScheduler.scheduleIfEnabled(this@QuickPanelService)
            }.onFailure { AppLog.e("Categorizing from quick panel failed", it) }
        }
    }

    /**
     * Safe from here: the user just tapped our visible overlay, which satisfies the
     * background-activity-start rules on Android 14/15.
     */
    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.onFailure { AppLog.e("Could not open the app from the quick panel", it) }
        stopSelf()
    }

    private fun removePanel() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
                .onFailure { AppLog.w("removing quick panel failed", it) }
        }
        overlayView = null
        viewOwner?.destroy()
        viewOwner = null
    }

    override fun onDestroy() {
        removePanel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Quick actions")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quick actions", NotificationManager.IMPORTANCE_MIN)
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "quick_panel"
    }
}
