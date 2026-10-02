package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/**
 * Vector de features serializable, separado de [BehaviorFeatures] para poder
 * evolucionar el esquema de persistencia sin tocar el extractor de dominio.
 * Nombres de campos estables: este JSONL se convierte en el dataset de
 * entrenamiento en Python.
 */
@Serializable
data class TrainingFeatures(
    val advIntervalMs: Double? = null,
    val manufacturerPayloadBytes: Int = 0,
    val serviceDataBytes: Int = 0,
    val serviceUuidCount: Int = 0,
    val hasRandomizedMac: Boolean = false,
    val appearanceCode: Int? = null,
    val txPowerDbm: Int? = null,
    val rssiMean: Double = 0.0,
    val rssiStdDev: Double = 0.0,
    val rssiSampleCount: Int = 0,
    val companyId: Int? = null,
    val payloadEntropyBits: Double = 0.0,
    val nameLength: Int = 0,
    val durationMs: Long = 0L,
) {
    companion object {
        fun from(f: BehaviorFeatures): TrainingFeatures = TrainingFeatures(
            advIntervalMs = f.advIntervalMs,
            manufacturerPayloadBytes = f.manufacturerPayloadBytes,
            serviceDataBytes = f.serviceDataBytes,
            serviceUuidCount = f.serviceUuidCount,
            hasRandomizedMac = f.hasRandomizedMac,
            appearanceCode = f.appearanceCode,
            txPowerDbm = f.txPowerDbm,
            rssiMean = f.rssiMean,
            rssiStdDev = f.rssiStdDev,
            rssiSampleCount = f.rssiSampleCount,
            companyId = f.companyId,
            payloadEntropyBits = f.payloadEntropyBits,
            nameLength = f.nameLength,
            durationMs = f.durationMs,
        )
    }
}

/**
 * Etiqueta del operador. `UNSURE` es una etiqueta válida: el modelo aprende
 * que ese punto es ruidoso. `null` significa no etiquetado (no entra al dataset).
 */
@Serializable
enum class TrainingLabel {
    FINDER_TAG,
    ROTATING_PHONE,
    FIXED_BEACON,
    WEARABLE_PERSONAL,
    AUDIO_ACCESSORY,
    VEHICLE_OR_CAR_KEY,
    SENSOR_FIXED,
    IOT_DEVICE,
    UNSURE,
    ;

    fun toBehavioralKind(): BehavioralKind? = when (this) {
        FINDER_TAG -> BehavioralKind.FINDER_TAG
        ROTATING_PHONE -> BehavioralKind.ROTATING_PHONE
        FIXED_BEACON -> BehavioralKind.FIXED_BEACON
        WEARABLE_PERSONAL -> BehavioralKind.WEARABLE_PERSONAL
        AUDIO_ACCESSORY -> BehavioralKind.AUDIO_ACCESSORY
        VEHICLE_OR_CAR_KEY -> BehavioralKind.VEHICLE_OR_CAR_KEY
        SENSOR_FIXED -> BehavioralKind.SENSOR_FIXED
        IOT_DEVICE -> BehavioralKind.IOT_DEVICE
        UNSURE -> null
    }

    companion object {
        fun from(kind: BehavioralKind): TrainingLabel? = when (kind) {
            BehavioralKind.FINDER_TAG -> FINDER_TAG
            BehavioralKind.ROTATING_PHONE -> ROTATING_PHONE
            BehavioralKind.FIXED_BEACON -> FIXED_BEACON
            BehavioralKind.WEARABLE_PERSONAL -> WEARABLE_PERSONAL
            BehavioralKind.AUDIO_ACCESSORY -> AUDIO_ACCESSORY
            BehavioralKind.VEHICLE_OR_CAR_KEY -> VEHICLE_OR_CAR_KEY
            BehavioralKind.SENSOR_FIXED -> SENSOR_FIXED
            BehavioralKind.IOT_DEVICE -> IOT_DEVICE
            BehavioralKind.UNKNOWN -> null
        }
    }
}

/**
 * Una fila del dataset. Identidad = [deviceHash] (SHA-256 truncado de kind:mac,
 * 16 hex chars). El MAC crudo nunca toca disco.
 */
@Serializable
data class TrainingSample(
    val id: String,
    val at: Long,
    val radioKind: String,
    val deviceHash: String,
    val features: TrainingFeatures,
    /** Predicción del clasificador heurístico al momento de capturar. */
    val heuristicKind: String? = null,
    val heuristicConfidence: Float? = null,
    val heuristicReasons: List<String> = emptyList(),
    /** Etiqueta del operador. null = sin etiquetar. */
    val label: String? = null,
    val labeledAt: Long? = null,
    val notes: String? = null,
)