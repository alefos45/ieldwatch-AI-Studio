package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * FASE 2 — Tests de [BearingEstimator].
 *
 * El estimador calcula la dirección relativa al objetivo por vector-average
 * sobre el RSSI lineal (10^(rssi/10)) tras un barrido del usuario.
 *
 * Cobertura:
 *  - Sin muestras / con span insuficiente → Unknown.
 *  - Pico claro al Este desde yaw 0 → RIGHT.
 *  - Pico al Norte desde yaw 30 → SLIGHT_LEFT.
 *  - NaN en yaw o rssi no envenena el estado.
 *  - reset() limpia bins y samples.
 *  - peakRssi refleja la muestra más fuerte, no la media.
 */
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

    @Test
    fun nanInputDoesNotPoisonState() {
        val e = BearingEstimator()
        // Dos NaN, un yaw válido con rssi NaN, un sample válido.
        e.add(Float.NaN, -70.0, 100L)
        e.add(0f, Double.NaN, 200L)
        e.add(90f, -70.0, 300L)
        // Solo 1 sample válido → Unknown.
        val b = e.estimate(0f)
        assertEquals(BearingCue.UNKNOWN, b.cue)
        assertEquals(0, b.samples)
    }

    @Test
    fun resetClearsSamplesAndBins() {
        val e = BearingEstimator()
        var yaw = 0f
        while (yaw <= 180f) {
            e.add(yaw, -50.0, yaw.toLong() * 100L)
            yaw += 30f
        }
        // Sanity: con datos hay una estimación.
        val before = e.estimate(0f)
        assertTrue("antes de reset debe haber samples", before.samples >= 6)

        e.reset()
        val after = e.estimate(0f)
        assertEquals(BearingCue.UNKNOWN, after.cue)
        assertEquals(0, after.samples)
    }

    @Test
    fun peakRssiReflectsStrongestSample() {
        val e = BearingEstimator()
        var yaw = 0f
        while (yaw <= 180f) {
            val rssi = -50.0 - abs(yaw - 90f) * 0.3
            e.add(yaw, rssi, yaw.toLong() * 100L)
            yaw += 30f
        }
        // El pico está en yaw=90 con rssi = -50 exacto.
        val b = e.estimate(0f)
        assertEquals(-50.0, b.peakRssi, 1e-9)
    }
}