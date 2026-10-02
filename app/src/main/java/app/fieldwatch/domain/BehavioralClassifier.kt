package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/**
 * Clasificador de comportamiento. Suma de scores ponderados por clase,
 * luego normaliza a confianza. Equivale a un Naive Bayes con priors y
 * verosimilitudes asignadas por conocimiento del dominio — no por
 * entrenamiento. Cuando exista dataset, se reemplaza el cuerpo de [classify]
 * por inferencia TFLite, manteniendo [BehaviorFeatures] como input.
 *
 * Reglas clave:
 *  - Finder tag: MAC aleatorio + payload corto + (Find My / SmartTag / Tile)
 *    + nombre repetido en varios MACs.
 *  - Phone rotating: MAC aleatorio + payload Apple Continuity / Google
 *    + presencia corta.
 *  - Fixed beacon: MAC estable + Eddystone/iBeacon + RSSI estable.
 *  - Wearable: Appearance watch/wrist + Garmin/Fitbit/Oura + RSSI estable.
 *  - Audio: Fast Pair UUID / Apple Proximity Pairing / Appearance headset.
 *  - Vehicle: OEM company ID / Tesla VIN glob.
 *  - Sensor fijo: Ruuvi/Blue Maestro + RSSI extremadamente estable.
 *  - IoT: Tuya/Nest + manufacturer data con MAC aleatorio.
 */
@Serializable
enum class BehavioralKind(val label: String) {
    FINDER_TAG("Finder tag"),
    ROTATING_PHONE("Phone (rotating)"),
    FIXED_BEACON("Fixed beacon"),
    WEARABLE_PERSONAL("Wearable"),
    AUDIO_ACCESSORY("Audio accessory"),
    VEHICLE_OR_CAR_KEY("Vehicle / car key"),
    SENSOR_FIXED("Fixed sensor"),
    IOT_DEVICE("IoT device"),
    UNKNOWN("Unknown"),
}

data class BehavioralClass(
    val kind: BehavioralKind,
    val confidence: Float,
    /** Frases cortas: "randomized MAC", "Find My payload", "Fast Pair UUID"… */
    val because: List<String>,
) {
    companion object {
        val Unknown = BehavioralClass(BehavioralKind.UNKNOWN, 0f, emptyList())
    }
}

class BehavioralClassifier {

    fun classify(
        device: Sighting,
        features: BehaviorFeatures = BehaviorFeatures.of(device),
        rotationCount: Int = 0,
    ): BehavioralClass {
        val scores = HashMap<BehavioralKind, Double>(9)
        val reasons = HashMap<BehavioralKind, MutableList<String>>(9)

        fun add(kind: BehavioralKind, score: Double, reason: String) {
            if (score <= 0.0) return
            scores[kind] = (scores[kind] ?: 0.0) + score
            reasons.getOrPut(kind) { mutableListOf() }.add(reason)
        }

        val name = device.name.lowercase()
        val mfgHex = device.manufacturerDataHex.filter { it.isLetterOrDigit() }.uppercase()
        val mfgFirst = mfgHex.take(2)
        val uuids = device.serviceUuids
            .map { it.uppercase().filter { c -> c.isLetterOrDigit() } }
        val appearance = features.appearanceCode

        // ---- Finder tags ------------------------------------------------
        if (features.hasRandomizedMac) add(BehavioralKind.FINDER_TAG, 0.35, "randomized MAC")
        if (features.companyId == 0x004C && mfgFirst == "12") {
            add(BehavioralKind.FINDER_TAG, 0.9, "Find My / Offline Finding payload")
        }
        if (features.companyId == 0x0075) add(BehavioralKind.FINDER_TAG, 0.4, "Samsung SmartTag company")
        if (uuids.any { it.contains("FD5A") }) add(BehavioralKind.FINDER_TAG, 0.55, "SmartTag UUID")
        if (uuids.any { it.contains("FD44") }) add(BehavioralKind.FINDER_TAG, 0.55, "Find My UUID")
        if (uuids.any { it.contains("FEED") || it.contains("FEDD") }) {
            add(BehavioralKind.FINDER_TAG, 0.55, "Tile UUID")
        }
        if (name.contains("airtag") || name.contains("tile") || name.contains("smarttag") ||
            name.contains("chipolo") || name.contains("pebblebee")
        ) {
            add(BehavioralKind.FINDER_TAG, 0.6, "finder name")
        }
        if (features.manufacturerPayloadBytes in 1..12 && features.hasRandomizedMac) {
            add(BehavioralKind.FINDER_TAG, 0.2, "short randomized payload")
        }
        if (rotationCount >= 2) {
            add(BehavioralKind.FINDER_TAG, 0.5, "name on $rotationCount MACs (rotating)")
        }
        if (features.advIntervalMs?.let { it in 1_500.0..3_500.0 } == true) {
            add(BehavioralKind.FINDER_TAG, 0.25, "advertising ~2 s")
        }

        // ---- Phone rotating --------------------------------------------
        if (features.hasRandomizedMac) add(BehavioralKind.ROTATING_PHONE, 0.3, "randomized MAC")
        if (features.companyId == 0x004C && mfgFirst in APPLE_CONTINUITY) {
            add(BehavioralKind.ROTATING_PHONE, 0.8, "Apple Continuity 0x$mfgFirst")
        }
        if (features.companyId == 0x00E0) add(BehavioralKind.ROTATING_PHONE, 0.4, "Google company")
        if (features.manufacturerPayloadBytes > 12) add(BehavioralKind.ROTATING_PHONE, 0.2, "long payload")
        if (appearance == 0x0040 || appearance == 0x0080 || appearance == 0x01C0) {
            add(BehavioralKind.ROTATING_PHONE, 0.4, "appearance phone/computer")
        }
        if (features.hasRandomizedMac && features.durationMs < 5 * 60_000L) {
            add(BehavioralKind.ROTATING_PHONE, 0.2, "short duration")
        }

        // ---- Fixed beacon ----------------------------------------------
        if (!features.hasRandomizedMac) add(BehavioralKind.FIXED_BEACON, 0.35, "stable MAC")
        if (uuids.any { it.contains("FEAA") }) add(BehavioralKind.FIXED_BEACON, 0.6, "Eddystone UUID")
        if (features.companyId == 0x004C && mfgFirst == "02" && mfgHex.contains("15")) {
            add(BehavioralKind.FIXED_BEACON, 0.45, "iBeacon layout")
        }
        if (features.rssiStdDev < 4.0 && features.rssiSampleCount >= 8) {
            add(BehavioralKind.FIXED_BEACON, 0.25, "stable RSSI")
        }
        if (features.durationMs > 5 * 60_000L) add(BehavioralKind.FIXED_BEACON, 0.25, "long presence")

        // ---- Wearable --------------------------------------------------
        if (appearance != null && appearance in 0x00C0..0x01FF) {
            add(BehavioralKind.WEARABLE_PERSONAL, 0.6, "appearance watch/wrist")
        }
        if (features.companyId in WEARABLE_COMPANIES) {
            add(BehavioralKind.WEARABLE_PERSONAL, 0.65, "wearable company")
        }
        if (features.rssiStdDev < 4.0 &&
            features.rssiSampleCount >= 8 &&
            features.advIntervalMs?.let { it < 2_000.0 } == true
        ) {
            add(BehavioralKind.WEARABLE_PERSONAL, 0.25, "stable fast advertising")
        }

        // ---- Audio accessory -------------------------------------------
        if (uuids.any { it.contains("FE2C") }) add(BehavioralKind.AUDIO_ACCESSORY, 0.55, "Fast Pair UUID")
        if (features.companyId == 0x004C && mfgFirst == "07") {
            add(BehavioralKind.AUDIO_ACCESSORY, 0.7, "Apple Proximity Pairing")
        }
        if (appearance == 0x0C40 || appearance == 0x0941 || appearance == 0x0940) {
            add(BehavioralKind.AUDIO_ACCESSORY, 0.6, "appearance headset/headphones")
        }
        if (features.companyId in AUDIO_COMPANIES) {
            add(BehavioralKind.AUDIO_ACCESSORY, 0.4, "audio company")
        }

        // ---- Vehicle ---------------------------------------------------
        if (features.companyId in VEHICLE_COMPANIES) {
            add(BehavioralKind.VEHICLE_OR_CAR_KEY, 0.65, "OEM vehicle company")
        }
        if (TESLA_VIN_GLOB.matches(name)) {
            add(BehavioralKind.VEHICLE_OR_CAR_KEY, 0.55, "Tesla VIN-key glob")
        }
        if (name.contains("tesla") || name.contains("mychevy") ||
            name.contains("uconnect") || name.contains("carplay")
        ) {
            add(BehavioralKind.VEHICLE_OR_CAR_KEY, 0.45, "vehicle name")
        }

        // ---- Fixed sensor ----------------------------------------------
        if (features.companyId in SENSOR_COMPANIES) {
            add(BehavioralKind.SENSOR_FIXED, 0.75, "Ruuvi/Blue Maestro company")
        }
        if (features.rssiStdDev < 2.0 && features.rssiSampleCount >= 20) {
            add(BehavioralKind.SENSOR_FIXED, 0.4, "extremely stable RSSI")
        }

        // ---- IoT -------------------------------------------------------
        if (features.companyId in IOT_COMPANIES) {
            add(BehavioralKind.IOT_DEVICE, 0.45, "IoT company")
        }
        if (features.companyId != null &&
            features.manufacturerPayloadBytes >= 4 &&
            features.hasRandomizedMac
        ) {
            add(BehavioralKind.IOT_DEVICE, 0.15, "generic manufacturer data")
        }

        // Baseline: nada tiene score cero absoluto.
        scores[BehavioralKind.UNKNOWN] = (scores[BehavioralKind.UNKNOWN] ?: 0.0) + 0.12

        val winner = scores.maxByOrNull { it.value } ?: return BehavioralClass.Unknown
        val total = scores.values.sum().coerceAtLeast(0.001)
        val confidence = (winner.value / total).toFloat().coerceIn(0f, 1f)
        if (winner.key == BehavioralKind.UNKNOWN || winner.value < 0.35) {
            return BehavioralClass.Unknown
        }
        return BehavioralClass(
            kind = winner.key,
            confidence = confidence,
            because = reasons[winner.key].orEmpty().distinct().take(4),
        )
    }

    private companion object {
        /** Apple Continuity types — no incluye 0x02 (iBeacon) ni 0x12 (Find My). */
        val APPLE_CONTINUITY = setOf(
            "05", "07", "08", "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
        )
        val WEARABLE_COMPANIES = setOf(
            0x0087, // Garmin
            0x018E, // Fitbit
            0x02B2, // Oura
        )
        val AUDIO_COMPANIES = setOf(
            0x009E, // Bose
            0x0057, // JBL / Harman
            0x012D, // Sony
        )
        val VEHICLE_COMPANIES = setOf(
            0x022B, // Tesla
            0x0723, // Ford
            0x0915, // Honda
            0x0826, // Hyundai
            0x0977, // Toyota
            0x0BA6, // Nissan
            0x0A10, // Subaru
            0x05EB, // BMW
            0x011F, // VW
            0x0120, // Porsche
            0x020B, // JLR
            0x0C34, // BYD
        )
        val SENSOR_COMPANIES = setOf(
            0x0499, // Ruuvi
            0x0133, // Blue Maestro
        )
        val IOT_COMPANIES = setOf(
            0x07D0, // Tuya
            0x01B5, // Nest
            0x02E5, // Nest (alt)
        )
        val TESLA_VIN_GLOB = Regex("""s[a-z0-9]{16,}[cdpr]""", RegexOption.IGNORE_CASE)
    }
}