package app.fieldwatch.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Dirección relativa al rumbo actual del teléfono. */
enum class BearingCue {
    UNKNOWN,
    AHEAD,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    LEFT,
    RIGHT,
    BEHIND,
}

data class Bearing(
    /** Firmado, -180..180. Positivo = girar a la derecha. NaN si desconocido. */
    val relativeDeg: Float,
    /** 0..1. Longitud del vector resultante: cuán concentrada está la energía. */
    val confidence: Float,
    val cue: BearingCue,
    val samples: Int,
    /** RSSI (filtrado) del pico; útil para mostrar. */
    val peakRssi: Double,
) {
    companion object {
        val Unknown = Bearing(Float.NaN, 0f, BearingCue.UNKNOWN, 0, Double.NaN)
    }
}

/**
 * Estimación de dirección por barrido (rotate-to-find).
 *
 * El cuerpo del usuario bloquea RF cuando se interpone. Al girar sobre sí
 * mismo, el RSSI suavizado describe una curva con un máximo en el rumbo
 * hacia el objetivo. Tomamos el rumbo medio circular ponderado por potencia
 * lineal (10^(rssi/10)) como dirección del objetivo.
 *
 * Vector-average (media circular) en lugar de peak-search:
 *  - Sobrevive a la discontinuidad 0°/360°.
 *  - Robusto a outliers.
 *  - O(n) trivial, sin ajuste de curva.
 *
 * Exige que el usuario haya barrido al menos [minSpanDeg] (bins de 30°) para
 * reportar dirección. Sin barrido, cualquier lectura sería "Ahead" trivial.
 */
class BearingEstimator(
    private val windowMs: Long = 12_000L,
    private val minSamples: Int = 6,
    private val minSpanDeg: Int = 120,
) {
    private data class Sample(val yaw: Float, val rssi: Double, val at: Long)

    private val samples = ArrayDeque<Sample>()
    private val bins = BooleanArray(12)      // 12 bins de 30°
    private var binCount = 0

    @Synchronized
    fun add(yawDeg: Float, rssi: Double, at: Long): Bearing {
        if (yawDeg.isNaN() || rssi.isNaN()) return estimate(yawDeg)
        val yaw = normalize(yawDeg)
        samples.addLast(Sample(yaw, rssi, at))
        evict(at)
        trackSpan(yaw)
        return estimate(yaw)
    }

    @Synchronized
    fun estimate(currentYaw: Float): Bearing {
        if (samples.size < minSamples) return Bearing.Unknown
        if (binCount * 30 < minSpanDeg) return Bearing.Unknown
        if (currentYaw.isNaN()) return Bearing.Unknown

        var cx = 0.0
        var cy = 0.0
        var wSum = 0.0
        var peak = Double.NEGATIVE_INFINITY
        for (s in samples) {
            // Potencia lineal: +10 dB pesa 10x, no comprime la dirección.
            val w = Math.pow(10.0, s.rssi / 10.0)
            val a = Math.toRadians(s.yaw.toDouble())
            cx += w * cos(a)
            cy += w * sin(a)
            wSum += w
            if (s.rssi > peak) peak = s.rssi
        }
        if (wSum <= 0.0) return Bearing.Unknown

        val meanYaw = Math.toDegrees(atan2(cy, cx)).toFloat()
        val resultant = (hypot(cx, cy) / wSum).toFloat().coerceIn(0f, 1f)
        val relative = shortestDelta(normalize(currentYaw), normalize(meanYaw))
        return Bearing(
            relativeDeg = relative,
            confidence = resultant,
            cue = cueFor(relative, resultant),
            samples = samples.size,
            peakRssi = peak,
        )
    }

    @Synchronized
    fun reset() {
        samples.clear()
        bins.fill(false)
        binCount = 0
    }

    private fun evict(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().at > windowMs) {
            samples.removeFirst()
        }
    }

    private fun trackSpan(yaw: Float) {
        val b = ((yaw / 30f).toInt()).coerceIn(0, 11)
        if (!bins[b]) {
            bins[b] = true
            binCount++
        }
    }

    private fun cueFor(relative: Float, confidence: Float): BearingCue {
        if (confidence < 0.15f) return BearingCue.UNKNOWN
        val a = abs(relative)
        return when {
            a <= 20f -> BearingCue.AHEAD
            a <= 60f -> if (relative < 0) BearingCue.SLIGHT_LEFT else BearingCue.SLIGHT_RIGHT
            a <= 120f -> if (relative < 0) BearingCue.LEFT else BearingCue.RIGHT
            else -> BearingCue.BEHIND
        }
    }

    private fun shortestDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    private fun normalize(yaw: Float): Float {
        var y = yaw % 360f
        if (y < 0f) y += 360f
        return y
    }
}