package com.smsexpense.tracker.service.quicklaunch

import kotlin.math.abs
import kotlin.math.sqrt

enum class BackTapSensitivity(val thresholdMs2: Float) {
    LOW(16f),
    MEDIUM(11f),
    HIGH(7f),
    ;

    companion object {
        fun fromName(name: String): BackTapSensitivity =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Detects three deliberate taps on the back of the device from raw accelerometer
 * samples. Pure logic with no Android dependencies so the tuning can actually be
 * unit-tested against synthetic signals.
 *
 * How it works:
 *  - Gravity is removed with a low-pass filter, leaving linear acceleration.
 *  - A tap is a short spike above [BackTapSensitivity.thresholdMs2]; each spike is
 *    counted once (edge-triggered) with a refractory gap so ringing is not
 *    double-counted.
 *  - Three spikes separated by [MIN_GAP_NS]..[MAX_GAP_NS] fire the gesture.
 *  - A slow "agitation" average rejects bursts while walking or while the phone is
 *    being picked up, which is where naive detectors produce false positives.
 */
class BackTapDetector(
    private var sensitivity: BackTapSensitivity = BackTapSensitivity.MEDIUM,
) {

    private var gravity = 9.81f
    private var initialized = false
    private var agitation = 0f
    private var aboveThreshold = false
    private var lastSpikeNs = 0L
    /** Null until the gesture has fired once; a 0 default would mute the first 1.5s. */
    private var lastFireNs: Long? = null
    private val spikes = ArrayDeque<Long>()

    fun setSensitivity(value: BackTapSensitivity) {
        sensitivity = value
        reset()
    }

    fun reset() {
        spikes.clear()
        aboveThreshold = false
        lastSpikeNs = 0L
    }

    /**
     * Feeds one sample. [timestampNs] is the sensor event timestamp.
     * Returns true exactly once when a triple tap is recognized.
     */
    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!initialized) {
            gravity = magnitude
            initialized = true
            return false
        }
        // Measure against the gravity estimate from BEFORE this sample: updating
        // first lets the low-pass filter swallow part of the very spike we are
        // trying to detect.
        val linear = abs(magnitude - gravity)
        gravity = GRAVITY_ALPHA * gravity + (1 - GRAVITY_ALPHA) * magnitude

        // Slow-moving activity level: high while walking / shaking, near zero at rest.
        agitation = AGITATION_ALPHA * agitation + (1 - AGITATION_ALPHA) * linear

        lastFireNs?.let { fired ->
            if (timestampNs - fired < REFRACTORY_AFTER_FIRE_NS) return false
        }

        val threshold = sensitivity.thresholdMs2
        if (linear >= threshold) {
            if (!aboveThreshold && timestampNs - lastSpikeNs >= MIN_SPIKE_GAP_NS) {
                aboveThreshold = true
                registerSpike(timestampNs)
                if (spikes.size >= TAPS_REQUIRED && agitation <= MAX_AGITATION) {
                    lastFireNs = timestampNs
                    reset()
                    return true
                }
            }
        } else if (linear < threshold * RELEASE_RATIO) {
            aboveThreshold = false
        }

        // Drop a stale sequence so two taps now plus one later never count.
        while (spikes.isNotEmpty() && timestampNs - spikes.first() > SEQUENCE_WINDOW_NS) {
            spikes.removeFirst()
        }
        return false
    }

    private fun registerSpike(timestampNs: Long) {
        if (spikes.isNotEmpty() && timestampNs - spikes.last() > MAX_GAP_NS) {
            // Too slow to be part of the same gesture: start over from this tap.
            spikes.clear()
        }
        spikes.addLast(timestampNs)
        lastSpikeNs = timestampNs
        while (spikes.size > TAPS_REQUIRED) spikes.removeFirst()
    }

    companion object {
        const val TAPS_REQUIRED = 3
        private const val GRAVITY_ALPHA = 0.85f
        private const val AGITATION_ALPHA = 0.97f
        /** Above this sustained level the device is clearly in motion, not resting. */
        private const val MAX_AGITATION = 2.2f
        private const val RELEASE_RATIO = 0.5f
        private const val MS = 1_000_000L
        private const val MIN_SPIKE_GAP_NS = 60 * MS
        private const val MAX_GAP_NS = 500 * MS
        private const val SEQUENCE_WINDOW_NS = 1_200 * MS
        private const val REFRACTORY_AFTER_FIRE_NS = 1_500 * MS
    }
}
