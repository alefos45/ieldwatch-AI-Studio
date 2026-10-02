package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/**
 * Lugar guardado por el operador. Se usa para decidir si el contexto actual
 * es "casa", "trabajo" o "desconocido". El operador guarda el lugar desde
 * Settings cuando está físicamente allí; la app calcula si el GPS actual
 * está dentro del radio.
 */
@Serializable
data class KnownPlace(
    val id: String,
    val kind: PlaceKind,
    val label: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double = 120.0,
) {
    fun contains(lat: Double, lon: Double): Boolean {
        if (kind == PlaceKind.UNKNOWN) return false
        return Geo.meters(this.lat, this.lon, lat, lon) <= radiusM
    }
}