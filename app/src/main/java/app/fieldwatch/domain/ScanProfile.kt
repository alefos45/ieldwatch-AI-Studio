package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/**
 * Perfil de escaneo efectivo decidido por [AdaptiveScanPolicy] o por el
 * operador en modo Manual. Los tres primeros coinciden con [ScanIntensity];
 * AGGRESSIVE es nuevo y solo se activa durante Hunt o búsqueda activa.
 */
@Serializable
enum class ScanProfile {
    SAVER, BALANCED, PERFORMANCE, AGGRESSIVE,
    ;

    fun label(): String = when (this) {
        SAVER -> "Battery saver"
        BALANCED -> "Balanced"
        PERFORMANCE -> "High performance"
        AGGRESSIVE -> "Aggressive (search)"
    }

    fun toScanIntensity(): ScanIntensity = when (this) {
        SAVER -> ScanIntensity.SAVER
        BALANCED -> ScanIntensity.BALANCED
        PERFORMANCE, AGGRESSIVE -> ScanIntensity.PERFORMANCE
    }
}

/**
 * Cómo decide el perfil de escaneo.
 *  - MANUAL: el operador elige [AppSettings.intensity] como hoy.
 *  - ADAPTIVE: el motor [AdaptiveScanPolicy] decide según contexto.
 */
@Serializable
enum class IntensityMode {
    MANUAL, ADAPTIVE,
    ;

    fun label(): String = when (this) {
        MANUAL -> "Manual"
        ADAPTIVE -> "Adaptive"
    }
}