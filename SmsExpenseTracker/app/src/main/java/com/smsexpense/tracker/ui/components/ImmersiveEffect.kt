package com.smsexpense.tracker.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smsexpense.tracker.util.disableImmersiveMode
import com.smsexpense.tracker.util.enableImmersiveMode

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Scoped immersive mode: while the calling composable is in composition, the
 * system navigation bar is hidden; when it leaves (back navigation, screen
 * change), the bar is restored so the rest of the app keeps its normal
 * system UI. Re-applies on ON_RESUME because the system can restore bars after
 * dialogs, app switches, or transient swipes across process pauses.
 */
@Composable
fun ImmersiveEffect() {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(view, lifecycleOwner) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            enableImmersiveMode(window)
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) enableImmersiveMode(window)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                disableImmersiveMode(window)
            }
        }
    }
}
