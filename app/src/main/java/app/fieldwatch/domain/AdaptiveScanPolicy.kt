package app.fieldwatch.domain

/**
 * Motor puro de decisión. Dada una [ContextState] y la configuración del
 * operador, devuelve el [ScanProfile] efectivo.
 *
 * No toca Android. Es testeable con JUnit puro. La integración con Android
 * vive en [app.fieldwatch.radio.ContextTracker] y [app.fieldwatch.radio.ScanService].
 *
 * Reglas (en orden de prioridad):
 *   1. Si el operador está en Manual, se respeta su elección.
 *   2. Si hay búsqueda activa (Hunt o Rastreo), se fuerza AGGRESSIVE.
 *   3. Si el operador se mueve, se prioriza detección → PERFORMANCE.
 *   4. Quieto, pantalla encendida:
 *        - lugar conocido → BALANCED (menos necesidad de barrer).
 *        - lugar desconocido → PERFORMANCE (posible entorno hostil).
 *   5. Quieto, pantalla apagada:
 *        - cargando → BALANCED (no importa la batería).
 *        - batería > 30% → BALANCED (margen suficiente).
 *        - batería ≤ 30% → SAVER.
 *   6. Nunca por debajo del [floor] elegido por el operador.
 */
object AdaptiveScanPolicy {

    fun decide(
        state: ContextState,
        mode: IntensityMode,
        floor: ScanProfile,
        manual: ScanIntensity,
        searchActive: Boolean,
    ): ScanProfile {
        if (mode == IntensityMode.MANUAL) {
            return manual.toProfile()
        }
        if (searchActive) return ScanProfile.AGGRESSIVE

        val base = when {
            state.activity.moving -> ScanProfile.PERFORMANCE

            state.screenOn && state.place == PlaceKind.UNKNOWN -> ScanProfile.PERFORMANCE
            state.screenOn -> ScanProfile.BALANCED

            state.batteryCharging -> ScanProfile.BALANCED
            state.batteryLevel > 30 -> ScanProfile.BALANCED
            else -> ScanProfile.SAVER
        }
        return maxOf(base, floor)
    }

    private fun ScanIntensity.toProfile(): ScanProfile = when (this) {
        ScanIntensity.SAVER -> ScanProfile.SAVER
        ScanIntensity.BALANCED -> ScanProfile.BALANCED
        ScanIntensity.PERFORMANCE -> ScanProfile.PERFORMANCE
    }

    /** Orden natural del enum: SAVER(0) < BALANCED(1) < PERFORMANCE(2) < AGGRESSIVE(3). */
    private fun maxOf(a: ScanProfile, b: ScanProfile): ScanProfile =
        if (a.ordinal >= b.ordinal) a else b
}