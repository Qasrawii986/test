package com.smsexpense.tracker.util

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImmersiveModeTest {

    @Test
    fun `enable sets swipe-transient behavior and is idempotent`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        enableImmersiveMode(activity.window)
        enableImmersiveMode(activity.window) // second call must be harmless

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            controller.systemBarsBehavior,
        )
    }

    @Test
    fun `disable restores default behavior and is idempotent`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        enableImmersiveMode(activity.window)
        disableImmersiveMode(activity.window)
        disableImmersiveMode(activity.window)

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
            controller.systemBarsBehavior,
        )
    }
}
