package com.smsexpense.tracker.settings

import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        settings.setBubbleStartY(777)
        settings.setBubbleSizeDp(88)

        val bubble = settings.bubbleSettings.first()
        assertEquals(90, bubble.autoHideSeconds)
        assertEquals(777, bubble.startY)
        assertEquals(88, bubble.sizeDp)
        assertTrue(bubble.enabled)
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
}
