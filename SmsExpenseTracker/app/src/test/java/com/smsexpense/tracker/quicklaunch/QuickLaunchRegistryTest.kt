package com.smsexpense.tracker.quicklaunch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchId
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchState
import com.smsexpense.tracker.service.quicklaunch.QuickLaunchRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickLaunchRegistryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `every method is described and addressable by id`() {
        val options = QuickLaunchRegistry.describeAll(context, backTapEnabled = false)
        assertEquals(QuickLaunchId.entries.size, options.size)
        QuickLaunchId.entries.forEach { id ->
            assertNotNull("no method registered for $id", QuickLaunchRegistry.byId(id))
            assertTrue(options.any { it.id == id })
        }
    }

    @Test
    fun `unsupported options always explain why`() {
        val options = QuickLaunchRegistry.describeAll(context, backTapEnabled = false)
        options.filter { !it.supported }.forEach { option ->
            assertNotNull("${option.id} is unsupported without a reason", option.unsupportedReason)
        }
    }

    @Test
    fun `supported options never carry an unsupported reason`() {
        QuickLaunchRegistry.describeAll(context, backTapEnabled = false)
            .filter { it.supported }
            .forEach { assertNull(it.unsupportedReason) }
    }

    @Test
    fun `sensor back tap reflects the stored on-off state`() {
        val off = QuickLaunchRegistry.describeAll(context, backTapEnabled = false)
            .single { it.id == QuickLaunchId.SENSOR_BACK_TAP }
        assertEquals(QuickLaunchState.INACTIVE, off.state)
        assertEquals("Turn on", off.actionLabel)

        val on = QuickLaunchRegistry.describeAll(context, backTapEnabled = true)
            .single { it.id == QuickLaunchId.SENSOR_BACK_TAP }
        assertEquals(QuickLaunchState.ACTIVE, on.state)
        assertEquals("Turn off", on.actionLabel)
    }

    @Test
    fun `battery-costly and role-stealing options carry a warning`() {
        val options = QuickLaunchRegistry.describeAll(context, backTapEnabled = false)
        assertNotNull(options.single { it.id == QuickLaunchId.SENSOR_BACK_TAP }.warning)
        assertNotNull(options.single { it.id == QuickLaunchId.ASSISTANT }.warning)
        // The cheap, always-safe option must not scare the user with a warning.
        assertNull(options.single { it.id == QuickLaunchId.QS_TILE }.warning)
    }
}
