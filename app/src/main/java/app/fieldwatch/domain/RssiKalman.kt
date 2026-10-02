package app.fieldwatch.domain

/**
 * Filtro de Kalman 1D para RSSI (dBm). El RSSI BLE es muy ruidoso por
 * multipath y bloqueo corporal. Suaviza sin el retardo de un promedio móvil
 * largo, y permite que el estimador de dirección vea una curva monótona
 * al girar.
 *
 * q = ruido de proceso (cuánto cambia el RSSI real entre paquetes).
 * r = varianza de la medida (dBm^2). Valores conservadores para BLE 1-20 Hz.
 */
class RssiKalman(
    private var q: Double = 0.15,
    private var r: Double = 4.0,
) {
    private var x: Double = Double.NaN
    private var p: Double = 1.0

    val current: Double? get() = if (x.isNaN()) null else x

    @Synchronized
    fun update(z: Double): Double {
        if (x.isNaN()) {
            x = z
            p = r
            return x
        }
        p += q
        val k = p / (p + r)
        x += k * (z - x)
        p *= (1.0 - k)
        return x
    }

    @Synchronized
    fun reset() {
        x = Double.NaN
        p = 1.0
    }

    @Synchronized
    fun retune(newQ: Double, newR: Double) {
        q = newQ
        r = newR
    }
}