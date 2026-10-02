package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 4 — política pura. Sin Android, sin ContextTracker.
 * Cubre:
 *  - Manual respeta la elección del operador.
 *  - searchActive fuerza AGGRESSIVE en Adaptive (y NO en Manual).
 *  - Actividad en movimiento → PERFORMANCE.
 *  - Pantalla encendida en lugar desconocido → PERFORMANCE.
 *  - Pantalla encendida en lugar conocido → BALANCED.
 *  - Pantalla apagada cargando → BALANCED.
 *  - Pantalla apagada batería > 30 → BALANCED.
 *  - Pantalla apagada batería ≤ 30 → SAVER.
 *  - floor nunca se rebasa hacia abajo.
 *  - floor AGGRESSIVE con base SAVER se mantiene AGGRESSIVE.
 */
class AdaptiveScanPolicyTest {

    private fun state(
        activity: ActivityKind = ActivityKind.STILL,
        place: PlaceKind = PlaceKind.UNKNOWN,
        screenOn: Boolean = true,
        batteryLevel: Int = 80,
        batteryCharging: Boolean = false,
    ) = ContextState(
        activity = activity,
        activityConfidence = 100,
        place = place,
        screenOn = screenOn,
        batteryLevel = batteryLevel,
        batteryCharging = batteryCharging,
        updatedAt = 0L,
    )

    // -----------------------------------------------------------------
    // MANUAL — la elección del operador manda
    // -----------------------------------------------------------------

    @Test
    fun manualReturnsManualChoice() {
        val s = state()
        assertEquals(
            ScanProfile.SAVER,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.SAVER, ScanIntensity.BALANCED,
                searchActive = false,
            ),
        )
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.SAVER, ScanIntensity.PERFORMANCE,
                searchActive = false,
            ),
        )
    }

    @Test
    fun manualIgnoresSearchActive() {
        // En Manual, la búsqueda activa no debe forzar AGGRESSIVE.
        val s = state()
        assertEquals(
            ScanProfile.SAVER,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = true,
            ),
        )
    }

    @Test
    fun manualIgnoresFloor() {
        // En Manual, el floor tampoco sube el perfil.
        val s = state(screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.SAVER,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.AGGRESSIVE, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    // -----------------------------------------------------------------
    // ADAPTIVE — searchActive
    // -----------------------------------------------------------------

    @Test
    fun searchActiveForcesAggressiveInAdaptive() {
        // Peor caso para Adaptive: pantalla apagada, batería baja, quieto.
        // Aun así, Hunt activo fuerza AGGRESSIVE.
        val s = state(screenOn = false, batteryLevel = 5, batteryCharging = false)
        assertEquals(
            ScanProfile.AGGRESSIVE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = true,
            ),
        )
    }

    @Test
    fun searchActiveAggressiveIsNotCappedByFloor() {
        // AGGRESSIVE es el ordinal más alto: maxOf(base, floor) no lo baja.
        val s = state(screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.AGGRESSIVE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.PERFORMANCE, ScanIntensity.SAVER,
                searchActive = true,
            ),
        )
    }

    // -----------------------------------------------------------------
    // ADAPTIVE — actividad
    // -----------------------------------------------------------------

    @Test
    fun walkingGivesPerformance() {
        val s = state(activity = ActivityKind.WALKING, screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun runningGivesPerformance() {
        // RUNNING no es nativo en esta implementación (degradado a WALKING),
        // pero el enum lo tiene y moving = true. La política debe responder igual.
        val s = state(activity = ActivityKind.RUNNING, screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun inVehicleGivesPerformance() {
        val s = state(activity = ActivityKind.IN_VEHICLE, screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun movingBeatsScreenOffAndLowBattery() {
        // Movimiento tiene prioridad sobre el resto de reglas.
        val s = state(
            activity = ActivityKind.WALKING,
            screenOn = false,
            batteryLevel = 5,
            batteryCharging = false,
        )
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    // -----------------------------------------------------------------
    // ADAPTIVE — pantalla encendida
    // -----------------------------------------------------------------

    @Test
    fun screenOnUnknownPlaceGivesPerformance() {
        val s = state(place = PlaceKind.UNKNOWN, screenOn = true, batteryLevel = 80)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOnHomeGivesBalanced() {
        val s = state(place = PlaceKind.HOME, screenOn = true, batteryLevel = 80)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOnWorkGivesBalanced() {
        val s = state(place = PlaceKind.WORK, screenOn = true, batteryLevel = 80)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOnOtherPlaceGivesBalanced() {
        val s = state(place = PlaceKind.OTHER, screenOn = true, batteryLevel = 80)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    // -----------------------------------------------------------------
    // ADAPTIVE — pantalla apagada
    // -----------------------------------------------------------------

    @Test
    fun screenOffChargingGivesBalanced() {
        // Cargando: no importa la batería.
        val s = state(screenOn = false, batteryCharging = true, batteryLevel = 5)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOffBatteryAbove30GivesBalanced() {
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 50)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOffBatteryAt31GivesBalanced() {
        // Frontera: la regla es `> 30`, así que 31 entra.
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 31)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOffBatteryAt30ExactlyGivesSaver() {
        // Frontera: `> 30` es false para 30 exacto → SAVER.
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 30)
        assertEquals(
            ScanProfile.SAVER,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun screenOffBatteryBelow30GivesSaver() {
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 20)
        assertEquals(
            ScanProfile.SAVER,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    // -----------------------------------------------------------------
    // Floor — nunca se rebasa hacia abajo
    // -----------------------------------------------------------------

    @Test
    fun floorRaisesSaverBaseToPerformance() {
        // Base = SAVER (pantalla off, batería 5%). Floor = PERFORMANCE.
        // El resultado debe ser PERFORMANCE, no SAVER.
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.PERFORMANCE, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun floorRaisesSaverBaseToBalanced() {
        val s = state(screenOn = false, batteryCharging = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.BALANCED, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun floorDoesNotLowerPerformanceBase() {
        // Base = PERFORMANCE (moviéndose). Floor = SAVER. No debe bajar.
        val s = state(activity = ActivityKind.WALKING, screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.PERFORMANCE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun floorDoesNotLowerBalancedBase() {
        val s = state(screenOn = true, place = PlaceKind.HOME)
        assertEquals(
            ScanProfile.BALANCED,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.SAVER, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun floorAggressiveWithSaverBaseStaysAggressive() {
        // Floor AGGRESSIVE es lo más alto posible. Base = SAVER.
        val s = state(screenOn = false, batteryLevel = 5)
        assertEquals(
            ScanProfile.AGGRESSIVE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.AGGRESSIVE, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    @Test
    fun floorAggressiveWithPerformanceBaseStaysAggressive() {
        val s = state(activity = ActivityKind.WALKING)
        assertEquals(
            ScanProfile.AGGRESSIVE,
            AdaptiveScanPolicy.decide(
                s, IntensityMode.ADAPTIVE, ScanProfile.AGGRESSIVE, ScanIntensity.SAVER,
                searchActive = false,
            ),
        )
    }

    // -----------------------------------------------------------------
    // Consistencia de ordinales
    // -----------------------------------------------------------------

    @Test
    fun scanProfileOrdinalsAreOrdered() {
        // El motor usa `maxOf(base, floor)` sobre ordinal. Si el orden
        // cambiara, toda la lógica de floor se rompe en silencio.
        assertEquals(0, ScanProfile.SAVER.ordinal)
        assertEquals(1, ScanProfile.BALANCED.ordinal)
        assertEquals(2, ScanProfile.PERFORMANCE.ordinal)
        assertEquals(3, ScanProfile.AGGRESSIVE.ordinal)
    }

    @Test
    fun manualMapsAllThreeScanIntensities() {
        // Guardarraíl: si alguien añade un cuarto ScanIntensity y olvida
        // mapearlo, este test falla.
        val s = state()
        for (intensity in ScanIntensity.entries) {
            val profile = AdaptiveScanPolicy.decide(
                s, IntensityMode.MANUAL, ScanProfile.SAVER, intensity,
                searchActive = false,
            )
            val expected = when (intensity) {
                ScanIntensity.SAVER -> ScanProfile.SAVER
                ScanIntensity.BALANCED -> ScanProfile.BALANCED
                ScanIntensity.PERFORMANCE -> ScanProfile.PERFORMANCE
            }
            assertEquals("intensity=$intensity", expected, profile)
        }
    }
}