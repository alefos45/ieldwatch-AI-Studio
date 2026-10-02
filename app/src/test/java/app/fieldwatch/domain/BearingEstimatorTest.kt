package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BearingEstimatorTest {
    @Test
    fun noSamplesReturnsUnknown() {
        val e = BearingEstimator()
        val b = e.estimate(0f)
        assertEquals(BearingCue.UNKNOWN, b.cue)
        assertTrue(b.relativeDeg.isNaN())
    }

    @Test
    fun stationarySixSamplesNotEnoughSpan() {
        val e = BearingEstimator()
        var last = Bearing.Unknown
        repeat(6) { i -> last = e.add(0f, -70.0, i * 100L) }
        assertEquals(BearingCue.UNKNOWN, last.cue)
    }

    @Test
    fun sweepZeroToTwoHundredPeakAtNinetyPointsRight() {
        val e = BearingEstimator()
        var yaw = 0f
        while (yaw <= 200f) {
            // RSSI máximo cerca de yaw=90° (bloqueo corporal del lado opuesto)
            val rssi = -50.0 - abs(yaw - 90f) * 0.3
            e.add(yaw, rssi, yaw.toLong() * 100L)
            yaw += 20f
        }
        val b = e.estimate(0f)
        assertEquals(BearingCue.RIGHT, b.cue)
        assertTrue("relative ≈ +90°, fue ${b.relativeDeg}", abs(b.relativeDeg - 90f) < 20f)
        assertTrue("confidence > 0.4, fue ${b.confidence}", b.confidence > 0.4f)
    }

    @Test
    fun peakAtZeroFromThirtyDegIsSlightLeft() {
        val e = BearingEstimator()
        var yaw = 0f
        while (yaw <= 180f) {
            val rssi = -50.0 - abs(yaw - 0f) * 0.4
            e.add(yaw, rssi, yaw.toLong() * 50L)
            yaw += 30f
        }
        val b = e.estimate(30f)
        assertEquals(BearingCue.SLIGHT_LEFT, b.cue)
        assertTrue("relative ≈ -30°, fue ${b.relativeDeg}", abs(b.relativeDeg + 30f) < 15f)
    }
}