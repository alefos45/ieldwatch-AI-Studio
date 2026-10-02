package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehavioralClassifierTest {
    private val classifier = BehavioralClassifier()

    @Test
    fun airTagIsFinderTag() {
        val device = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "12" + "00".repeat(24),
        )
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.FINDER_TAG, result.kind)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.because.any { it.contains("Find My", ignoreCase = true) })
    }

    @Test
    fun iphoneContinuityIsRotatingPhone() {
        val device = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "10AABBCCDDEE112233445566",
        )
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.ROTATING_PHONE, result.kind)
        assertTrue(result.because.any { it.contains("Continuity") })
    }

    @Test
    fun eddystoneIsFixedBeacon() {
        val device = ble(
            name = "",
            serviceUuids = listOf("FEAA"),
        ).copy(randomized = false)
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.FIXED_BEACON, result.kind)
    }

    @Test
    fun fastPairIsAudioAccessory() {
        val device = ble(
            name = "",
            serviceUuids = listOf("FE2C"),
        )
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.AUDIO_ACCESSORY, result.kind)
        assertTrue(result.because.any { it.contains("Fast Pair") })
    }

    @Test
    fun teslaVinKeyIsVehicle() {
        val device = ble(name = "S1a87a5a75f3df858C")
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.VEHICLE_OR_CAR_KEY, result.kind)
    }

    @Test
    fun ruuviIsFixedSensor() {
        val device = ble(
            name = "",
            manufacturerId = 0x0499,
            manufacturerDataHex = "05" + "00".repeat(20),
            samples = 30,
            stdDev = 1.0,
        )
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.SENSOR_FIXED, result.kind)
    }

    @Test
    fun unknownWithNoFeatures() {
        val device = ble(name = "")
        val result = classifier.classify(device)
        assertEquals(BehavioralKind.UNKNOWN, result.kind)
    }

    @Test
    fun rotationCountStrengthensFinderTag() {
        val a = ble(name = "Tag", manufacturerId = 0x004C, manufacturerDataHex = "12" + "00".repeat(24))
        val b = a.copy(key = "BLE:11:22:33:44:55:99", mac = "11:22:33:44:55:99")
        val rotation = RotationDetector.rotatingCount(a, listOf(a, b))
        assertEquals(1, rotation)
        val result = classifier.classify(a, rotationCount = rotation)
        assertEquals(BehavioralKind.FINDER_TAG, result.kind)
        assertTrue(result.because.any { it.contains("rotating") || it.contains("MACs") })
    }

    @Test
    fun noRotationForUniqueName() {
        val a = ble(name = "Tag")
        val b = ble(name = "Other")
        assertEquals(0, RotationDetector.rotatingCount(a, listOf(a, b)))
    }

    private fun ble(
        name: String,
        manufacturerId: Int? = null,
        manufacturerDataHex: String = "",
        serviceUuids: List<String> = emptyList(),
        samples: Int = 0,
        stdDev: Double = 0.0,
    ): Sighting {
        val rssiHistory = if (samples > 0) {
            (0 until samples).map { i ->
                // Variación determinista alrededor de -60 con la stddev pedida.
                val offset = if (i % 2 == 0) stdDev else -stdDev
                RssiSample(i * 500L, (-60 + offset).toInt())
            }
        } else {
            emptyList()
        }
        return Sighting(
            key = "BLE:AA:BB:CC:DD:EE:01",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:01",
            name = name,
            rssi = -60,
            rssiMin = -60,
            rssiMax = -60,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = true,
            hiddenSsid = false,
            serviceUuids = serviceUuids,
            manufacturerId = manufacturerId,
            manufacturerDataHex = manufacturerDataHex,
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = samples.coerceAtLeast(1),
            fleetIds = emptySet(),
            rssiHistory = rssiHistory,
            presence = emptyList(),
            facts = RadioFacts(
                mfgRecords = if (manufacturerId != null) {
                    listOf(MfgRecord(manufacturerId, manufacturerDataHex))
                } else {
                    emptyList()
                },
                addressType = "Random",
            ),
        )
    }
}