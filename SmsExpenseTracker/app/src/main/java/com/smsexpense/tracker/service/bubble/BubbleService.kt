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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
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
    private var dismissView: ComposeView? = null
    private var dismissOwner: OverlayViewOwner? = null
    internal val dismissVisible = MutableStateFlow(false)
    internal val dismissActive = MutableStateFlow(false)

    private val queue = ArrayDeque<Long>()
    internal val currentPayment = MutableStateFlow<Payment?>(null)
    internal val expanded = MutableStateFlow(false)
    internal val categories = MutableStateFlow<List<Category>>(emptyList())
    internal val queuedCount = MutableStateFlow(0)
    internal val payers = MutableStateFlow<List<com.smsexpense.tracker.domain.model.Payer>>(emptyList())
    internal val currentSplit =
        MutableStateFlow<com.smsexpense.tracker.domain.model.PaymentSplit?>(null)
    internal val panelMode = MutableStateFlow(BubblePanelMode.MAIN)
    /** Follows the shown payment so edits to its amount or split appear immediately. */
    private var watchJob: Job? = null
    /** Where the panel sat before an editor moved it clear of the keyboard. */
    private var positionBeforeEditing: Pair<Int, Int>? = null
    /** Where the finger has dragged to, which the magnet may override on screen. */
    private var freeX = 0f
    private var freeY = 0f
    internal val appearance = MutableStateFlow(
        com.smsexpense.tracker.domain.repository.BubbleSettings(
            enabled = true, autoHideSeconds = 45,
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
        serviceScope.launch {
            container.splitRepository.observePayers().collect { payers.value = it }
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
        watchJob?.cancel()
        panelMode.value = BubblePanelMode.MAIN
        currentSplit.value = null
        setOverlayFocusable(false)
        moveOutOfKeyboardWay(editing = false)
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
                watchPayment(nextId)
                ensureOverlay()
                startAutoHideTimer()
                return
            }
            // Already categorized / missing: skip to the next queued payment.
        }
    }

    /**
     * Keeps the panel in sync with the database while it is open: correcting the
     * amount or changing the split has to be reflected without reopening.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun watchPayment(paymentId: Long) {
        watchJob?.cancel()
        watchJob = serviceScope.launch {
            container.paymentRepository.observeById(paymentId)
                .onEach { updated -> if (updated != null) currentPayment.value = updated }
                .flatMapLatest { updated ->
                    if (updated == null) {
                        kotlinx.coroutines.flow.flowOf(null)
                    } else {
                        container.splitRepository.observeSplit(
                            paymentId, updated.amount, updated.currency,
                        )
                    }
                }
                .catch { AppLog.w("watching payment $paymentId failed", it) }
                .collect { currentSplit.value = it }
        }
    }

    private fun startAutoHideTimer() {
        autoHideJob?.cancel()
        // Never time out while the user is typing an amount or a name.
        if (panelMode.value != BubblePanelMode.MAIN) return
        autoHideJob = serviceScope.launch {
            val seconds = container.settingsRepository.bubbleSettings.first().autoHideSeconds
            // 0 means "wait until I deal with it": no timer at all. The bubble is
            // still dismissable by dragging it to the bin or tapping Later.
            if (seconds <= 0) return@launch
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
        val settings = container.settingsRepository.bubbleSettings.first()
        val (screenWidth, screenHeight) = screenSize()
        val bubblePx = bubbleSizePx()

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
            // Percentages of the usable area, so the saved spot lands correctly on
            // any screen size and stays fully on-screen.
            x = ((screenWidth - bubblePx) * settings.startXPercent).toInt().coerceAtLeast(0)
            y = ((screenHeight - bubblePx) * settings.startYPercent).toInt().coerceAtLeast(0)
        }
        layoutParams = params

        val owner = OverlayViewOwner().also { it.create() }
        viewOwner = owner

        val view = ComposeView(com.smsexpense.tracker.util.AppLocale.wrap(this)).apply {
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
                    onCollapse = ::onCollapsed,
                    appearanceFlow = appearance,
                    payersFlow = payers,
                    splitFlow = currentSplit,
                    modeFlow = panelMode,
                    onModeChange = ::onPanelModeChanged,
                    onChargeWholeTo = ::onChargeWholeTo,
                    onSaveSplit = ::onSaveSplit,
                    onSaveDetails = ::onSaveDetails,
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

    /**
     * Moves the bubble, then lets the dismiss target capture it.
     *
     * The finger position is tracked separately from the drawn position: once
     * the target captures the bubble it is pinned to the target's centre, so
     * the rendered position stops following the finger. Without a free
     * position to keep accumulating into, dragging back out would start from
     * the target and the bubble could never escape.
     */
    private fun moveBubbleBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        freeX += dx
        freeY += dy

        val captured = isWithinMagnet(freeX, freeY)
        if (captured) {
            val (targetX, targetY) = dismissTargetTopLeft()
            params.x = targetX
            params.y = targetY
        } else {
            params.x = freeX.toInt().coerceAtLeast(0)
            params.y = freeY.toInt().coerceAtLeast(0)
        }
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            AppLog.w("updateViewLayout failed", e)
        }

        if (captured != dismissActive.value) {
            dismissActive.value = captured
            // The system buzzes on capture and on escape; the snap is meant to
            // be felt, since the bubble is under the finger and hard to see.
            runCatching {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private fun onDragStarted() {
        autoHideJob?.cancel() // don't vanish mid-drag
        layoutParams?.let {
            freeX = it.x.toFloat()
            freeY = it.y.toFloat()
        }
        showDismissTarget()
    }

    private fun onDragFinished() {
        val dismissed = dismissActive.value
        hideDismissTarget()
        if (dismissed) {
            // Same outcome as "Later": keep the payment, drop the bubble.
            onDismissed()
        } else {
            if (appearance.value.snapToEdge) snapToNearestEdge()
            persistBubblePosition()
            startAutoHideTimer()
        }
    }

    /** Messenger-style: park the bubble against whichever side edge is closer. */
    private fun snapToNearestEdge() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val (width, _) = screenSize()
        if (width == 0) return
        val bubblePx = bubbleSizePx()
        val center = params.x + bubblePx / 2
        params.x = if (center < width / 2) EDGE_MARGIN_PX else width - bubblePx - EDGE_MARGIN_PX
        runCatching { windowManager?.updateViewLayout(view, params) }
            .onFailure { AppLog.w("snap to edge failed", it) }
    }

    private fun dismissTargetTopLeft(): Pair<Int, Int> {
        val (width, height) = screenSize()
        return DismissMagnet.snapTopLeft(
            bubbleSizePx(), width, height, resources.displayMetrics.density, bottomInsetPx(),
        )
    }

    private fun isWithinMagnet(x: Float, y: Float): Boolean {
        val (width, height) = screenSize()
        return DismissMagnet.captures(
            x, y, bubbleSizePx(), width, height, resources.displayMetrics.density, bottomInsetPx(),
        )
    }

    /**
     * Height of the navigation bar. Read from WindowMetrics where available and
     * otherwise from the gap between the real display and the usable area,
     * which is the only way to see it before Android 11.
     */
    private fun bottomInsetPx(): Int {
        val wm = windowManager ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.navigationBars())
                .bottom
        } else {
            @Suppress("DEPRECATION")
            val real = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            @Suppress("DEPRECATION")
            val usable = android.util.DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
            (real.heightPixels - usable.heightPixels).coerceAtLeast(0)
        }
    }

    /** Uses the configured bubble size so the dismiss zone matches what is on screen. */
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

    private fun showDismissTarget() {
        if (dismissView != null) {
            dismissVisible.value = true
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
        val view = ComposeView(com.smsexpense.tracker.util.AppLocale.wrap(this)).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { DismissTarget(visibleFlow = dismissVisible, activeFlow = dismissActive) }
        }
        dismissVisible.value = true
        dismissActive.value = false
        try {
            windowManager?.addView(view, params)
            dismissView = view
            dismissOwner = owner
        } catch (e: Exception) {
            AppLog.w("Could not show the dismiss target", e)
            owner.destroy()
            dismissVisible.value = false
        }
    }

    private fun hideDismissTarget() {
        dismissVisible.value = false
        dismissActive.value = false
        dismissView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                AppLog.w("removing the dismiss target failed", e)
            }
        }
        dismissView = null
        dismissOwner?.destroy()
        dismissOwner = null
    }

    private fun persistBubblePosition() {
        if (!appearance.value.rememberPosition) return
        val params = layoutParams ?: return
        val (width, height) = screenSize()
        val bubblePx = bubbleSizePx()
        val usableWidth = (width - bubblePx).coerceAtLeast(1)
        val usableHeight = (height - bubblePx).coerceAtLeast(1)
        val xPercent = params.x.toFloat() / usableWidth
        val yPercent = params.y.toFloat() / usableHeight
        serviceScope.launch(Dispatchers.IO) {
            container.settingsRepository.setBubblePosition(xPercent, yPercent)
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

    private fun onCollapsed() {
        expanded.value = false
        onPanelModeChanged(BubblePanelMode.MAIN)
    }

    /**
     * Text input needs a focusable window, and an overlay is created without
     * focus so it never steals touches from the app underneath. Editing flips
     * that for as long as the editor is open, then flips it straight back.
     */
    private fun onPanelModeChanged(mode: BubblePanelMode) {
        panelMode.value = mode
        val editing = mode != BubblePanelMode.MAIN
        setOverlayFocusable(editing)
        moveOutOfKeyboardWay(editing)
        if (editing) autoHideJob?.cancel() else startAutoHideTimer()
    }

    /**
     * The panel keeps whatever position the bubble was dragged to, which can be
     * exactly where the keyboard is about to appear. Park it near the top while
     * an editor is open, then put it back.
     */
    private fun moveOutOfKeyboardWay(editing: Boolean) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        if (editing) {
            if (positionBeforeEditing == null) positionBeforeEditing = params.x to params.y
            params.x = EDGE_MARGIN_PX
            params.y = TOP_MARGIN_WHILE_EDITING_PX
        } else {
            val restored = positionBeforeEditing ?: return
            params.x = restored.first
            params.y = restored.second
            positionBeforeEditing = null
        }
        runCatching { windowManager?.updateViewLayout(view, params) }
            .onFailure { AppLog.w("repositioning the panel failed", it) }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        // FLAG_LAYOUT_NO_LIMITS lets the bubble sit under the status bar, but it
        // also stops the window from being resized for the keyboard.
        val noLimits = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.flags = if (focusable) {
            params.flags and notFocusable.inv() and noLimits.inv()
        } else {
            params.flags or notFocusable or noLimits
        }
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        runCatching { windowManager?.updateViewLayout(view, params) }
            .onFailure { AppLog.w("toggling overlay focus failed", it) }
    }

    private fun onChargeWholeTo(payerId: Long) {
        val payment = currentPayment.value ?: return
        serviceScope.launch {
            runCatching {
                container.splitRepository.chargeWholePayment(payment.id, payerId, payment.amount)
            }.onFailure { AppLog.e("charging payment ${payment.id} failed", it) }
            startAutoHideTimer()
        }
    }

    private fun onSaveSplit(amounts: Map<Long, Double>) {
        val payment = currentPayment.value ?: return
        serviceScope.launch {
            runCatching { container.splitRepository.setSplit(payment.id, amounts) }
                .onFailure { AppLog.e("saving split for ${payment.id} failed", it) }
        }
    }

    private fun onSaveDetails(merchant: String?, amount: Double) {
        val payment = currentPayment.value ?: return
        serviceScope.launch {
            runCatching {
                container.paymentRepository.updateDetails(payment.id, merchant, amount)
            }.onFailure { AppLog.e("editing payment ${payment.id} failed", it) }
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
        watchJob?.cancel()
        hideDismissTarget()
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
        private const val EDGE_MARGIN_PX = 8
        private const val TOP_MARGIN_WHILE_EDITING_PX = 48
    }
}
