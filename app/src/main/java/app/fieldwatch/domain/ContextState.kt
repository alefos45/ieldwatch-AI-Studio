package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/** Actividad del operador, derivada del ActivityRecognition nativo de Android. */
@Serializable
enum class ActivityKind {
    STILL, WALKING, RUNNING, ON_BICYCLE, IN_VEHICLE, UNKNOWN,
    ;

    fun label(): String = when (this) {
        STILL -> "Still"
        WALKING -> "Walking"
        RUNNING -> "Running"
        ON_BICYCLE -> "On bicycle"
        IN_VEHICLE -> "In vehicle"
        UNKNOWN -> "Unknown"
    }

    val moving: Boolean
        get() = this == WALKING || this == RUNNING || this == ON_BICYCLE || this == IN_VEHICLE
}

/** Lugar del operador. Desconocido hasta que guarda uno en Settings. */
@Serializable
enum class PlaceKind {
    HOME, WORK, OTHER, UNKNOWN,
    ;

    fun label(): String = when (this) {
        HOME -> "Home"
        WORK -> "Work"
        OTHER -> "Saved place"
        UNKNOWN -> "Unknown"
    }
}

/**
 * Snapshot inmutable del contexto del operador en un instante.
 * Producido por [app.fieldwatch.radio.ContextTracker] y consumido por
 * [AdaptiveScanPolicy] para decidir el [ScanProfile] efectivo.
 */
data class ContextState(
    val activity: ActivityKind = ActivityKind.UNKNOWN,
    val activityConfidence: Int = 0,
    val place: PlaceKind = PlaceKind.UNKNOWN,
    val screenOn: Boolean = true,
    val batteryLevel: Int = 100,
    val batteryCharging: Boolean = false,
    val updatedAt: Long = 0L,
) {
    companion object {
        val Unknown = ContextState()
    }
}