package app.fieldwatch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.fieldwatch.domain.BehavioralClass
import app.fieldwatch.domain.BehavioralKind
import app.fieldwatch.domain.BehaviorFeatures
import app.fieldwatch.domain.RadioFacts
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TrainingLabel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FASE 1.5 — Tests de [TrainingStore].
 *
 * Persistencia JSONL por día UTC. Reglas clave:
 *  - record() es idempotente dentro de la sesión (mismo deviceHash → false).
 *  - recordForced() salta el filtro de sesión pero respeta el dedup del archivo.
 *  - label() solo etiqueta filas con label == null (no re-etiqueta).
 *  - clearLabel() solo limpia filas con label != null.
 *  - counts() excluye UNSURE del conteo de etiquetadas.
 *  - readLabeled() excluye UNSURE y null.
 *  - deviceHash() es determinista y sensible a la MAC.
 *  - clear() borra todo el directorio de recolección.
 *
 * Requiere Robolectric por el Context en el constructor.
 */
@RunWith(RobolectricTestRunner::class)
class TrainingStoreTest {

    private lateinit var context: Context
    private lateinit var store: TrainingStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = TrainingStore(context)
    }

    @After
    fun tearDown() {
        // Limpieza defensiva: si un test falla a medias, el siguiente
        // arranca con el directorio de recolección vacío.
        runBlocking { store.clear() }
    }

    // ----------------------------------------------------------------
    // record
    // ----------------------------------------------------------------

    @Test
    fun recordFirstTimeReturnsTrue() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        val features = BehaviorFeatures.of(device)
        val heuristic = BehavioralClass(BehavioralKind.FINDER_TAG, 0.8f, listOf("test"))

        val ok = store.record(device, features, heuristic)
        assertTrue(ok)

        val all = store.readAll()
        assertEquals(1, all.size)
        assertEquals("FINDER_TAG", all[0].heuristicKind)
        assertEquals("BLE", all[0].radioKind)
    }

    @Test
    fun recordSecondTimeSameDeviceReturnsFalse() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        val features = BehaviorFeatures.of(device)
        val heuristic = BehavioralClass(BehavioralKind.UNKNOWN, 0f, emptyList())

        assertTrue(store.record(device, features, heuristic))
        assertFalse(store.record(device, features, heuristic))

        assertEquals(1, store.readAll().size)
    }

    @Test
    fun recordDifferentDevicesBothSucceed() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        val features = BehaviorFeatures.of(a)
        val heuristic = BehavioralClass(BehavioralKind.UNKNOWN, 0f, emptyList())

        assertTrue(store.record(a, features, heuristic))
        assertTrue(store.record(b, features, heuristic))
        assertEquals(2, store.readAll().size)
    }

    // ----------------------------------------------------------------
    // recordForced
    // ----------------------------------------------------------------

    @Test
    fun recordForcedBypassesSessionFilter() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        val features = BehaviorFeatures.of(device)
        val heuristic = BehavioralClass(BehavioralKind.UNKNOWN, 0f, emptyList())

        // Primera escritura: acepta.
        assertTrue(store.recordForced(device, features, heuristic))
        // Segunda: el archivo ya tiene el hash → false.
        assertFalse(store.recordForced(device, features, heuristic))
        assertEquals(1, store.readAll().size)
    }

    // ----------------------------------------------------------------
    // deviceHash
    // ----------------------------------------------------------------

    @Test
    fun deviceHashIsDeterministic() {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:01")
        assertEquals(store.deviceHash(a), store.deviceHash(b))
    }

    @Test
    fun deviceHashDiffersByMac() {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        assertNotEquals(store.deviceHash(a), store.deviceHash(b))
    }

    // ----------------------------------------------------------------
    // label
    // ----------------------------------------------------------------

    @Test
    fun labelPersistsAndReadsBack() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        store.record(device, BehaviorFeatures.of(device), BehavioralClass.Unknown)
        val hash = store.deviceHash(device)

        assertTrue(store.label(hash, TrainingLabel.FINDER_TAG))
        assertEquals(TrainingLabel.FINDER_TAG, store.labelFor(hash))
    }

    @Test
    fun labelReturnsFalseIfAlreadyLabeled() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        store.record(device, BehaviorFeatures.of(device), BehavioralClass.Unknown)
        val hash = store.deviceHash(device)

        assertTrue(store.label(hash, TrainingLabel.FINDER_TAG))
        // Segunda vez: la fila ya tiene label != null, no cambia.
        assertFalse(store.label(hash, TrainingLabel.ROTATING_PHONE))
        // La primera etiqueta se conserva.
        assertEquals(TrainingLabel.FINDER_TAG, store.labelFor(hash))
    }

    @Test
    fun clearLabelRemovesLabel() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        store.record(device, BehaviorFeatures.of(device), BehavioralClass.Unknown)
        val hash = store.deviceHash(device)
        store.label(hash, TrainingLabel.FINDER_TAG)

        assertTrue(store.clearLabel(hash))
        assertNull(store.labelFor(hash))
        // La fila sigue existiendo, solo pierde la etiqueta.
        assertEquals(1, store.readAll().size)
    }

    @Test
    fun clearLabelReturnsFalseIfNoLabel() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        store.record(device, BehaviorFeatures.of(device), BehavioralClass.Unknown)
        val hash = store.deviceHash(device)

        // Nunca fue etiquetada.
        assertFalse(store.clearLabel(hash))
    }

    @Test
    fun hasLabelReflectsState() = runTest {
        val device = ble("AA:BB:CC:DD:EE:01")
        store.record(device, BehaviorFeatures.of(device), BehavioralClass.Unknown)
        val hash = store.deviceHash(device)

        assertFalse(store.hasLabel(hash))
        store.label(hash, TrainingLabel.WEARABLE_PERSONAL)
        assertTrue(store.hasLabel(hash))
    }

    // ----------------------------------------------------------------
    // counts / readLabeled / UNSURE
    // ----------------------------------------------------------------

    @Test
    fun countsIncludesOnlyLabeled() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        store.record(a, BehaviorFeatures.of(a), BehavioralClass.Unknown)
        store.record(b, BehaviorFeatures.of(b), BehavioralClass.Unknown)
        store.label(store.deviceHash(a), TrainingLabel.FINDER_TAG)

        val (total, labeled) = store.counts()
        assertEquals(2, total)
        assertEquals(1, labeled)
    }

    @Test
    fun countsExcludesUnsure() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        store.record(a, BehaviorFeatures.of(a), BehavioralClass.Unknown)
        store.record(b, BehaviorFeatures.of(b), BehavioralClass.Unknown)
        store.label(store.deviceHash(a), TrainingLabel.FINDER_TAG)
        store.label(store.deviceHash(b), TrainingLabel.UNSURE)

        val (total, labeled) = store.counts()
        assertEquals(2, total)
        // UNSURE no cuenta como etiqueta válida para el dataset.
        assertEquals(1, labeled)
    }

    @Test
    fun readLabeledExcludesUnsure() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        val c = ble("AA:BB:CC:DD:EE:03")
        store.record(a, BehaviorFeatures.of(a), BehavioralClass.Unknown)
        store.record(b, BehaviorFeatures.of(b), BehavioralClass.Unknown)
        store.record(c, BehaviorFeatures.of(c), BehavioralClass.Unknown)
        store.label(store.deviceHash(a), TrainingLabel.FINDER_TAG)
        store.label(store.deviceHash(b), TrainingLabel.UNSURE)
        // c queda sin etiquetar.

        val labeled = store.readLabeled()
        assertEquals(1, labeled.size)
        assertEquals("FINDER_TAG", labeled[0].label)
    }

    // ----------------------------------------------------------------
    // clear / export
    // ----------------------------------------------------------------

    @Test
    fun clearEmptiesEverything() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        val b = ble("AA:BB:CC:DD:EE:02")
        store.record(a, BehaviorFeatures.of(a), BehavioralClass.Unknown)
        store.record(b, BehaviorFeatures.of(b), BehavioralClass.Unknown)

        val removed = store.clear()
        assertEquals(2, removed)
        assertTrue(store.readAll().isEmpty())
        assertEquals(0 to 0, store.counts())
    }

    @Test
    fun exportCombinedProducesNonEmptyFile() = runTest {
        val a = ble("AA:BB:CC:DD:EE:01")
        store.record(a, BehaviorFeatures.of(a), BehavioralClass.Unknown)
        store.label(store.deviceHash(a), TrainingLabel.FINDER_TAG)

        val out = store.exportCombined()
        assertTrue("el archivo exportado debe existir", out.exists())
        assertTrue("el archivo no debe estar vacío", out.length() > 0)
    }

    // ----------------------------------------------------------------
    // Helper: crea un Sighting BLE sintético
    // ----------------------------------------------------------------

    private fun ble(mac: String, name: String = ""): Sighting = Sighting(
        key = "BLE:$mac",
        kind = RadioKind.BLE,
        mac = mac,
        name = name,
        rssi = -60,
        rssiMin = -60,
        rssiMax = -60,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1_000L,
        lastSeen = 1_000L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts.Empty,
    )
}