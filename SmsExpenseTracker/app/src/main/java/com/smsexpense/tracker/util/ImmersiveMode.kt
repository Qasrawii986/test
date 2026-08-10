package com.smsexpense.tracker.util

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * True immersive mode for a single screen: the system navigation bar (gesture
 * pill or 3-button bar) is fully hidden and the app window expands to the
 * bottom edge. A swipe from the edge reveals the bars transiently; they
 * auto-hide again (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
 *
 * Implemented with WindowInsetsControllerCompat — no deprecated
 * SYSTEM_UI_FLAG_* usage; the compat layer translates correctly for
 * API 26–29 and uses WindowInsetsController natively on 30+.
 *
 * The status bar is intentionally left visible: only the bottom bar area was
 * the problem, and top app bars keep their normal layout.
 */
fun enableImmersiveMode(window: Window) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.navigationBars())
}

/** Restores the navigation bar. Safe to call even if immersive mode is not active. */
fun disableImmersiveMode(window: Window) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    controller.show(WindowInsetsCompat.Type.navigationBars())
}
