package com.smsexpense.tracker.service.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.service.notification.PaymentNotifier
import com.smsexpense.tracker.service.sync.SyncScheduler
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the floating bubble overlay. Payments queue up if
 * several arrive before the user reacts; the service stops itself once the queue
 * is empty (categorized, dismissed, or auto-hidden).
 */
class BubbleService : Service() {

    private lateinit var container: AppContainer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var viewOwner: OverlayViewOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Drag-to-dismiss target (separate overlay window pinned to the bottom).
    private var trashView: ComposeView? = null
    private var trashOwner: OverlayViewOwner? = null
    internal val trashVisible = MutableStateFlow(false)
    internal val trashActive = MutableStateFlow(false)

    private val queue = ArrayDeque<Long>()
    internal val currentPayment = MutableStateFlow<Payment?>(null)
    internal val expanded = MutableStateFlow(false)
    internal val categories = MutableStateFlow<List<Category>>(emptyList())
    internal val queuedCount = MutableStateFlow(0)
    internal val appearance = MutableStateFlow(
        com.smsexpense.tracker.domain.repository.BubbleSettings(
            enabled = true, autoHideSeconds = 45, startY = 300,
        )
    )
    private var autoHideJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = appContainer()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        serviceScope.launch {
            container.categoryRepository.observeAll().collect { categories.value = it }
        }
        // Live appearance: changing size/shape/colour in settings updates the bubble.
        serviceScope.launch {
            container.settingsRepository.bubbleSettings.collect { appearance.value = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val paymentId = intent?.getLongExtra(EXTRA_PAYMENT_ID, -1L) ?: -1L
        if (paymentId > 0 && paymentId !in queue && currentPayment.value?.id != paymentId) {
            queue.addLast(paymentId)
        }
        queuedCount.value = queue.size
        if (currentPayment.value == null) {
            serviceScope.launch { showNext() }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLog.e("startForeground failed", e)
            stopSelf()
        }
    }

    private suspend fun showNext() {
        autoHideJob?.cancel()
        while (true) {
            val nextId = queue.removeFirstOrNull()
            queuedCount.value = queue.size
            if (nextId == null) {
                currentPayment.value = null
                removeOverlay()
                stopSelf()
                return
            }
            val payment = try {
                container.paymentRepository.getById(nextId)
            } catch (e: Exception) {
                AppLog.e("Failed to load payment $nextId", e)
                null
            }
            if (payment != null && payment.status == PaymentStatus.UNCATEGORIZED) {
                currentPayment.value = payment
                expanded.value = false
                ensureOverlay()
                startAutoHideTimer()
                return
            }
            // Already categorized / missing: skip to the next queued payment.
        }
    }

    private fun startAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = serviceScope.launch {
            val seconds = container.settingsRepository.bubbleSettings.first().autoHideSeconds
            delay(seconds * 1000L)
            // Timeout: leave the payment UNCATEGORIZED, but leave a notification
            // behind so it is never silently forgotten.
            currentPayment.value?.let { payment ->
                runCatching {
                    PaymentNotifier.notifyUncategorized(
                        this@BubbleService, payment, categories.value,
                    )
                }
            }
            showNext()
        }
    }

    private suspend fun ensureOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            AppLog.w("Overlay permission missing at display time")
            showNextSafely()
            return
        }
        val startY = container.settingsRepository.bubbleSettings.first().startY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = startY
        }
        layoutParams = params

        val owner = OverlayViewOwner().also { it.create() }
        viewOwner = owner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BubbleOverlay(
                    paymentFlow = currentPayment,
                    categoriesFlow = categories,
                    expandedFlow = expanded,
                    queuedCountFlow = queuedCount,
                    onTap = ::onBubbleTapped,
                    onDrag = ::moveBubbleBy,
                    onDragStart = ::onDragStarted,
                    onDragEnd = ::onDragFinished,
                    onCategorySelected = ::onCategorySelected,
                    onDismiss = ::onDismissed,
                    onCollapse = { expanded.value = false },
                    appearanceFlow = appearance,
                )
            }
        }
        overlayView = view
        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            AppLog.e("Failed to add overlay view", e)
            overlayView = null
            showNextSafely()
        }
    }

    private fun onBubbleTapped() {
        expanded.value = true
        startAutoHideTimer() // give the user a fresh window while the panel is open
    }

    private fun moveBubbleBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.x = (params.x + dx.toInt()).coerceAtLeast(0)
        params.y = (params.y + dy.toInt()).coerceAtLeast(0)
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            AppLog.w("updateViewLayout failed", e)
        }
        trashActive.value = isOverTrash(params)
    }

    private fun onDragStarted() {
        autoHideJob?.cancel() // don't vanish mid-drag
        showTrashTarget()
    }

    private fun onDragFinished() {
        val droppedOnTrash = trashActive.value
        hideTrashTarget()
        if (droppedOnTrash) {
            // Same outcome as "Later": keep the payment, drop the bubble.
            onDismissed()
        } else {
            persistBubblePosition()
            startAutoHideTimer()
        }
    }

    /** Bottom-centre hot zone: the lower sixth of the screen, middle half horizontally. */
    private fun isOverTrash(params: WindowManager.LayoutParams): Boolean {
        val (width, height) = screenSize()
        if (width == 0 || height == 0) return false
        val half = bubbleSizePx() / 2
        val bubbleCenterX = params.x + half
        val bubbleCenterY = params.y + half
        val inBottomBand = bubbleCenterY > height * 0.8f
        val inCentreBand = bubbleCenterX > width * 0.25f && bubbleCenterX < width * 0.75f
        return inBottomBand && inCentreBand
    }

    /** Uses the configured bubble size so the trash zone matches what is on screen. */
    private fun bubbleSizePx(): Int =
        (appearance.value.sizeDp * resources.displayMetrics.density).toInt()

    private fun screenSize(): Pair<Int, Int> {
        val wm = windowManager ?: return 0 to 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun showTrashTarget() {
        if (trashView != null) {
            trashVisible.value = true
            return
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        val owner = OverlayViewOwner().also { it.create() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { TrashTarget(visibleFlow = trashVisible, activeFlow = trashActive) }
        }
        trashVisible.value = true
        trashActive.value = false
        try {
            windowManager?.addView(view, params)
            trashView = view
            trashOwner = owner
        } catch (e: Exception) {
            AppLog.w("Could not show trash target", e)
            owner.destroy()
            trashVisible.value = false
        }
    }

    private fun hideTrashTarget() {
        trashVisible.value = false
        trashActive.value = false
        trashView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                AppLog.w("removing trash target failed", e)
            }
        }
        trashView = null
        trashOwner?.destroy()
        trashOwner = null
    }

    private fun persistBubblePosition() {
        val y = layoutParams?.y ?: return
        serviceScope.launch(Dispatchers.IO) {
            container.settingsRepository.setBubbleStartY(y)
        }
    }

    private fun onCategorySelected(categoryId: Long) {
        val payment = currentPayment.value ?: return
        serviceScope.launch {
            try {
                container.paymentRepository.categorize(payment.id, categoryId)
                PaymentNotifier.cancel(this@BubbleService, payment.id)
                SyncScheduler.scheduleIfEnabled(this@BubbleService)
            } catch (e: Exception) {
                AppLog.e("Failed to categorize payment ${payment.id}", e)
            }
            showNext()
        }
    }

    private fun onDismissed() {
        serviceScope.launch { showNext() }
    }

    private fun showNextSafely() {
        serviceScope.launch { showNext() }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                AppLog.w("removeView failed", e)
            }
        }
        overlayView = null
        layoutParams = null
        viewOwner?.destroy()
        viewOwner = null
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
        hideTrashTarget()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(R.string.bubble_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bubble_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.bubble_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_PAYMENT_ID = "payment_id"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bubble"
    }
}
