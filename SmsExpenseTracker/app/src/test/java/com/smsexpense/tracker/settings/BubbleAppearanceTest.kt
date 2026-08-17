package com.smsexpense.tracker.settings

import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleAppearanceTest {

    private val settings = FakeSettingsRepository()

    @Test
    fun `defaults are sane`() = runTest {
        val bubble = settings.bubbleSettings.first()
        assertEquals(BubbleSettings.DEFAULT_SIZE_DP, bubble.sizeDp)
        assertEquals(BubbleShape.CIRCLE, bubble.shape)
        assertNull(bubble.colorArgb)
        assertEquals(1f, bubble.opacity, 0.001f)
        assertTrue(bubble.showAmount)
    }

    @Test
    fun `size is clamped to a usable range`() = runTest {
        settings.setBubbleSizeDp(5)
        assertEquals(BubbleSettings.MIN_SIZE_DP, settings.bubbleSettings.first().sizeDp)

        settings.setBubbleSizeDp(500)
        assertEquals(BubbleSettings.MAX_SIZE_DP, settings.bubbleSettings.first().sizeDp)
    }

    @Test
    fun `opacity never drops to invisible`() = runTest {
        settings.setBubbleOpacity(0f)
        assertEquals(
            BubbleSettings.MIN_OPACITY,
            settings.bubbleSettings.first().opacity,
            0.001f,
        )
    }

    @Test
    fun `shape colour and content round-trip`() = runTest {
        settings.setBubbleShape(BubbleShape.SQUARE)
        settings.setBubbleColor(0xFFC62828)
        settings.setBubbleShowAmount(false)

        val bubble = settings.bubbleSettings.first()
        assertEquals(BubbleShape.SQUARE, bubble.shape)
        assertEquals(0xFFC62828, bubble.colorArgb)
        assertTrue(!bubble.showAmount)
    }

    @Test
    fun `clearing the colour falls back to the theme`() = runTest {
        settings.setBubbleColor(0xFF1565C0)
        settings.setBubbleColor(null)
        assertNull(settings.bubbleSettings.first().colorArgb)
    }

    @Test
    fun `appearance changes do not disturb behaviour settings`() = runTest {
        settings.setBubbleAutoHideSeconds(90)
        settings.setBubblePosition(0.9f, 0.2f)
        settings.setBubbleSizeDp(88)

        val bubble = settings.bubbleSettings.first()
        assertEquals(90, bubble.autoHideSeconds)
        assertEquals(0.9f, bubble.startXPercent, 0.001f)
        assertEquals(0.2f, bubble.startYPercent, 0.001f)
        assertEquals(88, bubble.sizeDp)
        assertTrue(bubble.enabled)
    }

    @Test
    fun `position is stored as a clamped fraction of the screen`() = runTest {
        settings.setBubblePosition(-0.5f, 3f)
        val bubble = settings.bubbleSettings.first()
        assertEquals(0f, bubble.startXPercent, 0.001f)
        assertEquals(1f, bubble.startYPercent, 0.001f)
    }

    @Test
    fun `remember position and edge snapping default on and toggle`() = runTest {
        assertTrue(settings.bubbleSettings.first().rememberPosition)
        assertTrue(settings.bubbleSettings.first().snapToEdge)

        settings.setBubbleRememberPosition(false)
        settings.setBubbleSnapToEdge(false)

        val bubble = settings.bubbleSettings.first()
        assertTrue(!bubble.rememberPosition)
        assertTrue(!bubble.snapToEdge)
    }

    @Test
    fun `every preset colour is either a theme fallback or fully opaque`() {
        BubbleSettings.PRESET_COLORS.forEach { argb ->
            if (argb != null) {
                val alpha = (argb ushr 24) and 0xFF
                assertEquals("preset $argb must be opaque", 0xFF, alpha)
            }
        }
    }

    @Test
    fun `the background image path round-trips and clears`() = runTest {
        assertNull(settings.bubbleSettings.first().backgroundPath)

        settings.setBubbleBackground("/data/user/0/app/files/bubble_bg_1.png")
        assertEquals(
            "/data/user/0/app/files/bubble_bg_1.png",
            settings.bubbleSettings.first().backgroundPath,
        )

        settings.setBubbleBackground(null)
        assertNull(settings.bubbleSettings.first().backgroundPath)
    }

    @Test
    fun `a blank background path is treated as no background`() = runTest {
        settings.setBubbleBackground("   ")
        assertNull(settings.bubbleSettings.first().backgroundPath)
    }

    @Test
    fun `auto-hide reaches ten minutes`() = runTest {
        // The old UI capped the slider at 180s; the limit is the stored maximum.
        settings.setBubbleAutoHideSeconds(600)
        assertEquals(600, settings.bubbleSettings.first().autoHideSeconds)
        assertFalse(settings.bubbleSettings.first().autoHideDisabled)
    }

    @Test
    fun `auto-hide above the maximum is clamped, not rejected`() = runTest {
        settings.setBubbleAutoHideSeconds(99_999)
        assertEquals(
            BubbleSettings.MAX_AUTO_HIDE_SECONDS,
            settings.bubbleSettings.first().autoHideSeconds,
        )
    }

    @Test
    fun `zero means never hide rather than hide instantly`() = runTest {
        settings.setBubbleAutoHideSeconds(BubbleSettings.NEVER_AUTO_HIDE)
        val bubble = settings.bubbleSettings.first()
        assertEquals(0, bubble.autoHideSeconds)
        assertTrue(bubble.autoHideDisabled)
    }

    @Test
    fun `a negative duration is normalised to never`() = runTest {
        settings.setBubbleAutoHideSeconds(-30)
        assertTrue(settings.bubbleSettings.first().autoHideDisabled)
    }

    @Test
    fun `a too-short duration is raised to the minimum`() = runTest {
        settings.setBubbleAutoHideSeconds(3)
        assertEquals(
            BubbleSettings.MIN_AUTO_HIDE_SECONDS,
            settings.bubbleSettings.first().autoHideSeconds,
        )
    }
}
