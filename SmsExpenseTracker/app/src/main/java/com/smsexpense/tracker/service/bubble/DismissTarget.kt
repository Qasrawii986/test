package com.smsexpense.tracker.service.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.R
import kotlinx.coroutines.flow.StateFlow

/**
 * The drag-to-dismiss target, drawn to match the one Android itself shows for
 * system bubbles: a dark circle with a white ✕ at the bottom of the screen,
 * over a fading scrim, growing as the bubble is captured.
 *
 * It has to be drawn by the app. The system's own target lives in SystemUI
 * (`com.android.wm.shell`, `MagnetizedObject` / `MagneticTarget`) and is not
 * reachable from a `TYPE_APPLICATION_OVERLAY` window — no public API exposes
 * it, so every floating-bubble app draws its own. This one copies the system's
 * appearance and its magnetic snap so it behaves like the real thing.
 *
 * Deliberately not themed: the system target is the same dark chrome in light
 * and dark mode, because it is drawn over whatever app is on screen.
 */
@Composable
fun DismissTarget(
    visibleFlow: StateFlow<Boolean>,
    activeFlow: StateFlow<Boolean>,
) {
    val visible by visibleFlow.collectAsState()
    val active by activeFlow.collectAsState()
    if (!visible) return

    // The captured state is what the system animates: the target swells slightly
    // and the ✕ brightens, so the snap is felt as well as seen.
    val scale by animateFloatAsState(
        targetValue = if (active) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "dismissScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCRIM_HEIGHT_DP.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                )
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = DismissMagnet.BOTTOM_MARGIN_DP.dp)
                .scale(scale)
                .size(DismissMagnet.CIRCLE_SIZE_DP.dp)
                .background(
                    color = Color.Black.copy(alpha = if (active) 0.85f else 0.6f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.bubble_dismiss),
                tint = Color.White,
                modifier = Modifier
                    .size(ICON_SIZE_DP.dp)
                    .alpha(if (active) 1f else 0.9f),
            )
        }
    }
}

private const val ICON_SIZE_DP = 24
private const val SCRIM_HEIGHT_DP = 200

/**
 * Where the dismiss target sits and when it captures the bubble.
 *
 * Pure geometry, deliberately outside the service: this is the part that
 * decides whether a drag ends in a dismissal, and it is worth testing without
 * a WindowManager.
 */
object DismissMagnet {

    /** Matches AOSP's dismiss circle so the target lands where the eye expects it. */
    const val CIRCLE_SIZE_DP = 52
    const val BOTTOM_MARGIN_DP = 40

    /**
     * How close the bubble's centre must come before the target captures it.
     * Roughly twice the circle, like the system's, which is what makes the
     * target feel as though it reaches out instead of having to be hit exactly.
     */
    const val MAGNET_RADIUS_DP = 90

    /**
     * Centre of the ✕ circle in screen pixels.
     *
     * [bottomInsetPx] is the navigation bar's height. The bubble's window uses
     * FLAG_LAYOUT_NO_LIMITS so its coordinates span the whole display, while
     * the target's window is laid out above the navigation bar — without
     * subtracting the inset the magnet would sit a nav bar's height below the
     * ✕ that is actually drawn, and on a 3-button nav bar that gap is wide
     * enough to miss the target entirely.
     */
    fun targetCenter(
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        bottomInsetPx: Int = 0,
    ): Pair<Float, Float> =
        screenWidth / 2f to
            screenHeight - bottomInsetPx - (BOTTOM_MARGIN_DP + CIRCLE_SIZE_DP / 2f) * density

    /**
     * True when a bubble whose top-left is ([x], [y]) is close enough to be
     * pulled in. Distance-based rather than a rectangular band, so the target
     * captures from any direction.
     */
    fun captures(
        x: Float,
        y: Float,
        bubbleSizePx: Int,
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        bottomInsetPx: Int = 0,
    ): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val half = bubbleSizePx / 2f
        val (targetX, targetY) = targetCenter(screenWidth, screenHeight, density, bottomInsetPx)
        val dx = (x + half) - targetX
        val dy = (y + half) - targetY
        val radius = MAGNET_RADIUS_DP * density
        return dx * dx + dy * dy <= radius * radius
    }

    /** Top-left the bubble must take to sit centred on the target. */
    fun snapTopLeft(
        bubbleSizePx: Int,
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        bottomInsetPx: Int = 0,
    ): Pair<Int, Int> {
        val (centerX, centerY) = targetCenter(screenWidth, screenHeight, density, bottomInsetPx)
        val half = bubbleSizePx / 2
        return (centerX - half).toInt() to (centerY - half).toInt()
    }
}
