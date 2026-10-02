package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        assertTrue("un salto de +20 dB no debe mover más de ~8 dB", abs(after - before) <= 8.0)
    }

    @Test
    fun resetForgetsState() {
        val k = RssiKalman()
        k.update(-40.0)
        k.reset()
        assertNull(k.current)
        assertEquals(-90.0, k.update(-90.0), 0.0)
    }
}