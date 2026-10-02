package app.fieldwatch.radio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Rumbo del dispositivo (azimut) desde el sensor de rotación.
 * El OS ya fusiona acelerómetro + giroscopio + magnetómetro.
 * SENSOR_DELAY_GAME (~50 Hz) es de sobra para girar sobre sí mismo.
 */
class RotationSensor(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val main = Handler(Looper.getMainLooper())
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile private var registered = false

    @Volatile var lastHeading: Float = Float.NaN
        private set

    @Volatile var available: Boolean = false
        private set

    fun start(): Boolean {
        if (registered) return true
        val s = sensor ?: return false
        available = manager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, main)
        registered = available
        return available
    }

    fun stop() {
        if (!registered) return
        manager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val azDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val h = ((azDeg % 360f) + 360f) % 360f
        lastHeading = h
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}