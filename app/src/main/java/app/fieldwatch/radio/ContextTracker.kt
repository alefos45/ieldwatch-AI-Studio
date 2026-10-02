package app.fieldwatch.radio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.ActivityKind
import app.fieldwatch.domain.ContextState
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.PlaceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FASE 4 (Bloque 3): contexto del operador.
 *
 * Combina señales nativas, sin Google Play Services:
 *  - Step detector (SensorManager.TYPE_STEP_DETECTOR) → WALKING.
 *  - Velocidad desde el operatorPath de FieldwatchApp → IN_VEHICLE.
 *  - Ausencia de steps y de velocidad reciente, pero con fix GPS → STILL.
 *  - Sin nada de lo anterior → UNKNOWN.
 *  - Fix GPS cruzado contra AppSettings.knownPlaces → HOME / WORK / OTHER.
 *  - BatteryManager → nivel + charging.
 *  - DeviceScreenState → screenOn.
 *
 * Limitación explícita: RUNNING y ON_BICYCLE no son detectables sin GMS.
 * Se degradan a WALKING e IN_VEHICLE respectivamente. Documentado para que
 * el operador no espere esa granularidad.
 *
 * Emite un snapshot nuevo solo si cambia algún campo semántico (activity,
 * place, screenOn, batería). updatedAt se actualiza en cada emisión.
 */
class ContextTracker(
    private val app: FieldwatchApp,
    private val screenState: DeviceScreenState,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ContextState())
    val state: StateFlow<ContextState> = _state.asStateFlow()

    private val sensorManager =
        app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepDetector: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val batteryManager =
        app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    @Volatile private var lastStepAt = 0L
    @Volatile private var lastSpeedMps = 0.0
    @Volatile private var lastSpeedAt = 0L
    private var tickJob: Job? = null

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
                lastStepAt = System.currentTimeMillis()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (tickJob?.isActive == true) return
        if (stepDetector != null) {
            runCatching {
                sensorManager?.registerListener(
                    stepListener,
                    stepDetector,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
            }
        }
        tickJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                refresh()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        runCatching { sensorManager?.unregisterListener(stepListener) }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        updateSpeed(now)
        val activity = inferActivity(now)
        val place = inferPlace()
        val screenOn = screenState.screenOn.value
        val battery = readBattery()
        val next = ContextState(
            activity = activity.first,
            activityConfidence = activity.second,
            place = place,
            screenOn = screenOn,
            batteryLevel = battery.first,
            batteryCharging = battery.second,
            updatedAt = now,
        )
        val prev = _state.value
        if (prev.activity == next.activity &&
            prev.place == next.place &&
            prev.screenOn == next.screenOn &&
            prev.batteryLevel == next.batteryLevel &&
            prev.batteryCharging == next.batteryCharging
        ) {
            return
        }
        _state.value = next
    }

    /**
     * Velocidad media sobre los últimos ~30 s del operatorPath. Si no hay
     * dos samples recientes, se conserva la última velocidad con su marca
     * temporal para no oscilar a 0 con un fix perdido.
     */
    private fun updateSpeed(now: Long) {
        val path = app.operatorPathCopy()
        if (path.size < 2) return
        val recent = path.filter { now - it.at <= SPEED_WINDOW_MS }
        if (recent.size < 2) return
        val d = Geo.pathLengthM(recent)
        val dt = (recent.last().at - recent.first().at).coerceAtLeast(1L)
        val mps = d / (dt / 1000.0)
        if (mps.isFinite()) {
            lastSpeedMps = mps
            lastSpeedAt = now
        }
    }

    private fun inferActivity(now: Long): Pair<ActivityKind, Int> {
        val stepRecent = now - lastStepAt <= STEP_WINDOW_MS
        val speedFresh = now - lastSpeedAt <= SPEED_FRESH_MS
        val speed = if (speedFresh) lastSpeedMps else 0.0
        return when {
            speed >= VEHICLE_MIN_MPS -> ActivityKind.IN_VEHICLE to 80
            stepRecent && speed >= WALK_MIN_MPS -> ActivityKind.WALKING to 75
            stepRecent -> ActivityKind.WALKING to 70
            speedFresh && speed >= WALK_MIN_MPS -> ActivityKind.WALKING to 60
            hasRecentFix(now) -> ActivityKind.STILL to 50
            else -> ActivityKind.UNKNOWN to 0
        }
    }

    private fun hasRecentFix(now: Long): Boolean {
        if (app.lastFix == null) return false
        val path = app.operatorPathCopy()
        if (path.isEmpty()) return true
        return now - path.last().at <= FIX_FRESH_MS
    }

    private fun inferPlace(): PlaceKind {
        val fix = app.lastFix ?: return PlaceKind.UNKNOWN
        val places = app.config.settings.knownPlaces
        if (places.isEmpty()) return PlaceKind.UNKNOWN
        val hit = places.firstOrNull { it.contains(fix.first, fix.second) }
        return hit?.kind ?: PlaceKind.UNKNOWN
    }

    private fun readBattery(): Pair<Int, Boolean> {
        val bm = batteryManager ?: return 100 to false
        val level = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(100).coerceIn(0, 100)
        val charging = runCatching { bm.isCharging }.getOrDefault(false)
        return level to charging
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val STEP_WINDOW_MS = 10_000L
        private const val SPEED_WINDOW_MS = 30_000L
        private const val SPEED_FRESH_MS = 45_000L
        private const val FIX_FRESH_MS = 60_000L
        /** ~2.2 km/h. Por debajo, no es caminar sostenido. */
        private const val WALK_MIN_MPS = 0.6
        /** ~10 km/h. Por encima, vehículo. */
        private const val VEHICLE_MIN_MPS = 2.8
    }
}