package app.fieldwatch.domain

import kotlin.math.log2
import kotlin.math.sqrt

data class BehaviorFeatures(
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
        fun of(
            device: Sighting,
            now: Long = if (device.lastSeen > 0L) device.lastSeen else System.currentTimeMillis(),
        ): BehaviorFeatures {
            val rssiSamples = device.rssiHistory
            val count = rssiSamples.size
            val (mean, stdDev) = if (count > 0) {
                val vals = rssiSamples.map { it.rssi.toDouble() }
                val m = vals.average()
                val variance = vals.map { (it - m) * (it - m) }.average()
                m to sqrt(variance)
            } else {
                device.rssi.toDouble() to 0.0
            }

            val mfgHex = device.manufacturerDataHex.filter { it.isLetterOrDigit() }
            val mfgBytes = mfgHex.length / 2

            val svcDataBytes = device.facts.serviceData.sumOf {
                it.dataHex.filter { c -> c.isLetterOrDigit() }.length / 2
            }

            val rawOrMfg = when {
                device.rawHex.isNotBlank() -> device.rawHex
                mfgHex.isNotBlank() -> mfgHex
                else -> ""
            }
            val entropy = calculateEntropy(rawOrMfg)

            val compId = device.manufacturerId
                ?: device.facts.mfgRecords.firstOrNull()?.companyId

            val isRandom = device.randomized ||
                device.facts.addressType?.equals("Random", ignoreCase = true) == true

            val advInterval = device.facts.advertisingIntervalMs ?: run {
                if (rssiSamples.size >= 2) {
                    val deltas = rssiSamples.zipWithNext { a, b -> (b.at - a.at).toDouble() }
                        .filter { it > 0.0 }
                    if (deltas.isNotEmpty()) deltas.average() else null
                } else null
            }

            val duration = (now - device.firstSeen).coerceAtLeast(0L)

            return BehaviorFeatures(
                advIntervalMs = advInterval,
                manufacturerPayloadBytes = mfgBytes,
                serviceDataBytes = svcDataBytes,
                serviceUuidCount = device.serviceUuids.size,
                hasRandomizedMac = isRandom,
                appearanceCode = device.facts.appearance,
                txPowerDbm = device.facts.txPowerDbm,
                rssiMean = mean,
                rssiStdDev = stdDev,
                rssiSampleCount = count,
                companyId = compId,
                payloadEntropyBits = entropy,
                nameLength = device.name.length,
                durationMs = duration,
            )
        }

        private fun calculateEntropy(hex: String): Double {
            val cleaned = hex.filter { it.isLetterOrDigit() }
            if (cleaned.length < 2) return 0.0
            val bytes = cleaned.chunked(2).mapNotNull { it.toIntOrNull(16) }
            if (bytes.isEmpty()) return 0.0
            val counts = bytes.groupingBy { it }.eachCount()
            val total = bytes.size.toDouble()
            return -counts.values.sumOf { count ->
                val p = count / total
                if (p > 0.0) p * log2(p) else 0.0
            }
        }
    }
}
