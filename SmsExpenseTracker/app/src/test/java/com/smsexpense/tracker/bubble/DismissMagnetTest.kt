package com.smsexpense.tracker.bubble

import com.smsexpense.tracker.service.bubble.DismissMagnet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry that decides whether letting go of the bubble dismisses it.
 * Getting this wrong either loses a payment bubble the user meant to keep or
 * makes the ✕ impossible to hit, so it is checked without a WindowManager.
 */
class DismissMagnetTest {

    // A typical phone at 3x density.
    private val width = 1080
    private val height = 2400
    private val density = 3f
    private val bubble = (64 * density).toInt()

    private fun captures(x: Float, y: Float) =
        DismissMagnet.captures(x, y, bubble, width, height, density)

    /** Top-left that places the bubble's centre exactly on the target. */
    private fun centredOnTarget(): Pair<Float, Float> {
        val (cx, cy) = DismissMagnet.targetCenter(width, height, density)
        return cx - bubble / 2f to cy - bubble / 2f
    }

    @Test
    fun `the target sits at the bottom centre of the screen`() {
        val (x, y) = DismissMagnet.targetCenter(width, height, density)
        assertEquals(width / 2f, x, 0.5f)
        // Above the bottom edge by the margin plus half the circle.
        val expected = height - (DismissMagnet.BOTTOM_MARGIN_DP + DismissMagnet.CIRCLE_SIZE_DP / 2f) * density
        assertEquals(expected, y, 0.5f)
        assertTrue("the target must stay on screen", y < height && y > height * 0.8f)
    }

    @Test
    fun `a bubble dropped on the target is captured`() {
        val (x, y) = centredOnTarget()
        assertTrue(captures(x, y))
    }

    @Test
    fun `the bubble parked at its usual edge position is not captured`() {
        // Default start position: left edge, 35% down. Dismissing must be deliberate.
        assertFalse(captures(0f, height * 0.35f))
    }

    @Test
    fun `the top of the screen never captures`() {
        assertFalse(captures(width / 2f, 0f))
        assertFalse(captures(0f, 0f))
        assertFalse(captures(width - bubble.toFloat(), 0f))
    }

    @Test
    fun `capture reaches out from every direction`() {
        val (x, y) = centredOnTarget()
        // Just inside the radius: above, left and right of the target all capture.
        val reach = (DismissMagnet.MAGNET_RADIUS_DP - 5) * density
        assertTrue("from above", captures(x, y - reach))
        assertTrue("from the left", captures(x - reach, y))
        assertTrue("from the right", captures(x + reach, y))
    }

    @Test
    fun `just outside the radius does not capture`() {
        val (x, y) = centredOnTarget()
        val beyond = (DismissMagnet.MAGNET_RADIUS_DP + 5) * density
        assertFalse(captures(x, y - beyond))
        assertFalse(captures(x - beyond, y))
    }

    @Test
    fun `the pull is a circle, not a square`() {
        val (x, y) = centredOnTarget()
        // A corner at the radius in both axes is further than the radius away,
        // so a rectangular hit test would wrongly capture it.
        val edge = DismissMagnet.MAGNET_RADIUS_DP * density * 0.9f
        assertFalse("diagonal corner should be out of reach", captures(x - edge, y - edge))
    }

    @Test
    fun `snapping centres the bubble on the target`() {
        val (left, top) = DismissMagnet.snapTopLeft(bubble, width, height, density)
        val (targetX, targetY) = DismissMagnet.targetCenter(width, height, density)
        assertEquals(targetX, left + bubble / 2f, 1f)
        assertEquals(targetY, top + bubble / 2f, 1f)
        // And a snapped bubble is, by definition, captured.
        assertTrue(captures(left.toFloat(), top.toFloat()))
    }

    @Test
    fun `the reach scales with screen density, not raw pixels`() {
        val ldpi = 1.5f
        val smallBubble = (64 * ldpi).toInt()
        val (cx, cy) = DismissMagnet.targetCenter(width, height, ldpi)
        val x = cx - smallBubble / 2f
        val y = cy - smallBubble / 2f
        val reach = (DismissMagnet.MAGNET_RADIUS_DP - 5) * ldpi

        assertTrue(DismissMagnet.captures(x, y - reach, smallBubble, width, height, ldpi))
        // The same pixel distance would be well outside the reach at this density.
        val highDensityReach = (DismissMagnet.MAGNET_RADIUS_DP - 5) * 3f
        assertFalse(
            DismissMagnet.captures(x, y - highDensityReach, smallBubble, width, height, ldpi),
        )
    }

    @Test
    fun `a bigger bubble is still measured from its centre`() {
        val big = (96 * density).toInt()
        val (left, top) = DismissMagnet.snapTopLeft(big, width, height, density)
        assertTrue(DismissMagnet.captures(left.toFloat(), top.toFloat(), big, width, height, density))
    }

    @Test
    fun `an unknown screen size never captures`() {
        // screenSize() returns 0x0 when the WindowManager is gone; dismissing on
        // that would be a drag the user never asked for.
        assertFalse(DismissMagnet.captures(0f, 0f, bubble, 0, 0, density))
        assertFalse(DismissMagnet.captures(500f, 500f, bubble, 0, 2400, density))
    }

    @Test
    fun `landscape puts the target at the new bottom centre`() {
        val landscapeWidth = 2400
        val landscapeHeight = 1080
        val (x, y) = DismissMagnet.targetCenter(landscapeWidth, landscapeHeight, density)
        assertEquals(landscapeWidth / 2f, x, 0.5f)
        assertTrue(y < landscapeHeight)
        assertTrue(
            DismissMagnet.captures(
                x - bubble / 2f, y - bubble / 2f, bubble, landscapeWidth, landscapeHeight, density,
            )
        )
    }

    @Test
    fun `the navigation bar shifts the target up by exactly its height`() {
        // The bubble's window spans the whole display; the target's window stops
        // above the navigation bar. Ignoring the inset put the magnet a nav bar
        // below the drawn ✕ — far enough on a 3-button bar to miss it entirely.
        val navBar = (48 * density).toInt()
        val (_, withoutBar) = DismissMagnet.targetCenter(width, height, density)
        val (_, withBar) = DismissMagnet.targetCenter(width, height, density, navBar)
        assertEquals(navBar.toFloat(), withoutBar - withBar, 0.5f)
    }

    @Test
    fun `with a nav bar the ✕ still captures where it is drawn`() {
        val navBar = (48 * density).toInt()
        val (left, top) = DismissMagnet.snapTopLeft(bubble, width, height, density, navBar)

        assertTrue(
            "snapping must land inside the magnet it snapped to",
            DismissMagnet.captures(
                left.toFloat(), top.toFloat(), bubble, width, height, density, navBar,
            ),
        )
        // And the old inset-blind position is now far enough away to be released
        // safely rather than silently dismissing the bubble.
        val (blindLeft, blindTop) = DismissMagnet.snapTopLeft(bubble, width, height, density)
        assertFalse(
            DismissMagnet.captures(
                blindLeft.toFloat(), blindTop.toFloat() + 2 * DismissMagnet.MAGNET_RADIUS_DP * density,
                bubble, width, height, density, navBar,
            ),
        )
    }

    @Test
    fun `gesture navigation with no bar behaves as before`() {
        val (a, b) = DismissMagnet.targetCenter(width, height, density, bottomInsetPx = 0)
        val (c, d) = DismissMagnet.targetCenter(width, height, density)
        assertEquals(a, c, 0.01f)
        assertEquals(b, d, 0.01f)
    }
}
