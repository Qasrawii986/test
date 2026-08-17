package com.smsexpense.tracker.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.repository.DataStoreSettingsRepository
import com.smsexpense.tracker.domain.repository.BubbleSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The clamping rules live in the real repository, so they are checked against
 * it rather than only against the fake used elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsTest {

    private val repository by lazy {
        DataStoreSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `auto-hide accepts the full range up to ten minutes`() = runTest {
        repository.setBubbleAutoHideSeconds(600)
        assertEquals(600, repository.bubbleSettings.first().autoHideSeconds)
    }

    @Test
    fun `zero is stored as never, not clamped up to the minimum`() = runTest {
        repository.setBubbleAutoHideSeconds(0)
        val bubble = repository.bubbleSettings.first()
        assertEquals(BubbleSettings.NEVER_AUTO_HIDE, bubble.autoHideSeconds)
        assertTrue(bubble.autoHideDisabled)
    }

    @Test
    fun `an out-of-range duration is clamped into the allowed band`() = runTest {
        repository.setBubbleAutoHideSeconds(5_000)
        assertEquals(
            BubbleSettings.MAX_AUTO_HIDE_SECONDS,
            repository.bubbleSettings.first().autoHideSeconds,
        )

        repository.setBubbleAutoHideSeconds(1)
        val bubble = repository.bubbleSettings.first()
        assertEquals(BubbleSettings.MIN_AUTO_HIDE_SECONDS, bubble.autoHideSeconds)
        assertFalse(bubble.autoHideDisabled)
    }

    @Test
    fun `the background path is stored and cleared`() = runTest {
        repository.setBubbleBackground("/data/files/bubble_bg_9.png")
        assertEquals(
            "/data/files/bubble_bg_9.png",
            repository.bubbleSettings.first().backgroundPath,
        )

        repository.setBubbleBackground(null)
        assertNull(repository.bubbleSettings.first().backgroundPath)
    }
}
