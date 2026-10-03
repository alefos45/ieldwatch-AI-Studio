package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * FASE 2 — Tests de [RssiKalman].
 *
 * El filtro es el preludio del BearingEstimator: sin un RSSI estable al
 * girar sobre uno mismo, el vector-average da direcciones basura.
 *
 * Cobertura:
 *  - Estado inicial y comportamiento de la primera muestra.
 *  - Convergencia con entrada constante.
 *  - Atenuación de outliers (un salto de +20 dB no se propaga).
 *  - reset() limpia todo.
 *  - retune(q, r) cambia parámetros sin resetear estado.
 *  - q alto rastrea cambios rápidos más agresivamente que q bajo.
 */
class RssiKalmanTest {

    @Test
    fun firstSampleIsReturnedAsIs() {
        val k = RssiKalman()
        assertNull(k.current)
        assertEquals(-70.0, k.update(-70.0), 0.0)
        assertEquals(-70.0, k.current!!, 0.0)
    }

    @Test
    fun convergesToStableInput() {
        val k = RssiKalman()
        repeat(30) { k.update(-60.0) }
        assertEquals(-60.0, k.current!!, 0.5)
    }

    @Test
    fun outlierIsAttenuated() {
        val k = RssiKalman()
        repeat(20) { k.update(-70.0) }
        val before = k.current!!
        val after = k.update(-50.0)
        assertTrue(
            "un salto de +20 dB no debe mover más de ~8 dB (antes=$before, después=$after)",
            abs(after - before) <= 8.0,
        )
    }

    @Test
    fun resetForgetsState() {
        val k = RssiKalman()
        k.update(-40.0)
        k.reset()
        assertNull(k.current)
        // Primera muestra después de reset vuelve a ser exacta.
        assertEquals(-90.0, k.update(-90.0), 0.0)
    }

    @Test
    fun retuneChangesParamsWithoutResettingEstimate() {
        // Estabiliza con parámetros conservadores.
        val k = RssiKalman(q = 0.01, r = 100.0)
        repeat(20) { k.update(-60.0) }
        assertEquals(-60.0, k.current!!, 0.01)

        // Cambia a parámetros agresivos. La estimación debe sobrevivir.
        k.retune(newQ = 5.0, newR = 0.1)
        assertEquals(-60.0, k.current!!, 0.01)

        // La próxima medida se sigue mucho más rápido que con los params viejos.
        val after = k.update(-40.0)
        assertTrue(
            "retune a q alto debe permitir un salto brusco (x=$after)",
            abs(after - (-40.0)) < 2.0,
        )
    }

    @Test
    fun higherProcessNoiseTracksFaster() {
        // Dos filtros estabilizados con la misma entrada.
        val lowQ = RssiKalman(q = 0.01, r = 4.0)
        val highQ = RssiKalman(q = 1.0, r = 4.0)
        repeat(20) {
            lowQ.update(-60.0)
            highQ.update(-60.0)
        }
        // El mismo paso (-40). El de q alto debe acercarse más a -40.
        val lowAfter = lowQ.update(-40.0)
        val highAfter = highQ.update(-40.0)
        assertTrue(
            "highQ=$highAfter debe estar más cerca de -40 que lowQ=$lowAfter",
            abs(highAfter - (-40.0)) < abs(lowAfter - (-40.0)),
        )
    }
}