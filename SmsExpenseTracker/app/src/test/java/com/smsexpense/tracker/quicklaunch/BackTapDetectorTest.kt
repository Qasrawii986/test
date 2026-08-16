package com.smsexpense.tracker.quicklaunch

import com.smsexpense.tracker.service.quicklaunch.BackTapDetector
import com.smsexpense.tracker.service.quicklaunch.BackTapSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Drives the detector with synthetic accelerometer streams so the tuning is
 * verified rather than assumed — false positives are the main risk of this feature.
 */
class BackTapDetectorTest {

    private val ms = 1_000_000L
    private val sampleIntervalNs = 10 * ms // ~100 Hz
    private val gravity = 9.81f

    private class Feeder(private val detector: BackTapDetector) {
        var fired = 0
        var now = 0L

        fun rest(durationMs: Long, jitter: Float = 0.05f, random: Random = Random(1)) {
            repeat((durationMs / 10).toInt()) {
                val noise = (random.nextFloat() - 0.5f) * 2 * jitter
                feed(9.81f + noise)
            }
        }

        fun tap(peak: Float = 22f) {
            // A tap is a couple of samples of sharp acceleration, then settling.
            feed(peak)
            feed(peak * 0.6f)
            feed(9.81f)
        }

        fun gap(durationMs: Long) = rest(durationMs)

        fun feed(magnitude: Float) {
            if (detector.onSample(now, magnitude, 0f, 0f)) fired++
            now += 10 * 1_000_000L
        }
    }

    private fun feeder(sensitivity: BackTapSensitivity = BackTapSensitivity.MEDIUM) =
        Feeder(BackTapDetector(sensitivity))

    @Test
    fun `three deliberate taps fire the gesture once`() {
        val f = feeder()
        f.rest(500)
        f.tap(); f.gap(150)
        f.tap(); f.gap(150)
        f.tap()
        f.rest(300)
        assertEquals(1, f.fired)
    }

    @Test
    fun `two taps do not fire`() {
        val f = feeder()
        f.rest(500)
        f.tap(); f.gap(150)
        f.tap()
        f.rest(800)
        assertEquals(0, f.fired)
    }

    @Test
    fun `taps spaced too far apart do not fire`() {
        val f = feeder()
        f.rest(500)
        f.tap(); f.gap(900)
        f.tap(); f.gap(900)
        f.tap()
        f.rest(300)
        assertEquals(0, f.fired)
    }

    @Test
    fun `resting device never fires`() {
        val f = feeder()
        f.rest(10_000)
        assertEquals(0, f.fired)
    }

    @Test
    fun `walking-like continuous motion does not fire`() {
        val f = feeder()
        val random = Random(7)
        // Sustained rhythmic motion around 1.5 g swings, like a phone in a pocket.
        repeat(1500) {
            val swing = kotlin.math.sin(it / 4.0).toFloat() * 6f
            val noise = (random.nextFloat() - 0.5f) * 3f
            f.feed(gravity + swing + noise)
        }
        assertEquals(0, f.fired)
    }

    @Test
    fun `single hard knock does not fire`() {
        val f = feeder()
        f.rest(500)
        f.tap(peak = 30f)
        f.rest(1000)
        assertEquals(0, f.fired)
    }

    @Test
    fun `refractory period prevents an immediate second trigger`() {
        val f = feeder()
        f.rest(500)
        f.tap(); f.gap(150); f.tap(); f.gap(150); f.tap()
        // Immediately tap three more times: still within the refractory window.
        f.gap(100)
        f.tap(); f.gap(150); f.tap(); f.gap(150); f.tap()
        assertEquals(1, f.fired)
    }

    @Test
    fun `a second gesture fires after the refractory window`() {
        val f = feeder()
        f.rest(500)
        f.tap(); f.gap(150); f.tap(); f.gap(150); f.tap()
        f.rest(2500)
        f.tap(); f.gap(150); f.tap(); f.gap(150); f.tap()
        assertEquals(2, f.fired)
    }

    @Test
    fun `low sensitivity ignores soft taps that high sensitivity accepts`() {
        // Magnitude 18 ≈ 8 m/s² of linear acceleration: a gentle tap.
        val soft = 18f

        val low = feeder(BackTapSensitivity.LOW)
        low.rest(500)
        low.tap(soft); low.gap(150); low.tap(soft); low.gap(150); low.tap(soft)
        assertEquals(0, low.fired)

        val high = feeder(BackTapSensitivity.HIGH)
        high.rest(500)
        high.tap(soft); high.gap(150); high.tap(soft); high.gap(150); high.tap(soft)
        assertEquals(1, high.fired)
    }

    @Test
    fun `sensitivity names round-trip and fall back safely`() {
        assertEquals(BackTapSensitivity.HIGH, BackTapSensitivity.fromName("HIGH"))
        assertEquals(BackTapSensitivity.MEDIUM, BackTapSensitivity.fromName("nonsense"))
    }

    @Test
    fun `reset clears a partial sequence`() {
        val detector = BackTapDetector(BackTapSensitivity.MEDIUM)
        val f = Feeder(detector)
        f.rest(500)
        f.tap(); f.gap(150)
        f.tap()
        detector.reset()
        f.gap(150)
        f.tap()
        f.rest(300)
        assertFalse(f.fired > 0)
    }

    @Test
    fun `detector tolerates a long random stream without firing`() {
        val f = feeder()
        val random = Random(42)
        repeat(20_000) {
            f.feed(gravity + (random.nextFloat() - 0.5f) * 4f)
        }
        assertTrue("random noise fired ${f.fired} times", f.fired == 0)
    }
}
