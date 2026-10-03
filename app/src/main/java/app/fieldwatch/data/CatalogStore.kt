package app.fieldwatch.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo externo de nombres (assets/fieldwatch_catalog.json).
 *
 * Fuente de verdad para:
 *   - Fast Pair model IDs   → fastPairName(0xCD8256) → "Bose NC 700"
 *   - Samsung model IDs     → samsungName("1234")    → "Galaxy Buds2 Pro"
 *
 * Fallback (si el binario no tiene el dato):
 *   - OUIs IEEE             → ouiName("B41E52")      → "Flock Safety"
 *   - Bluetooth Company IDs → companyName(0x004C)    → "Apple, Inc."
 *
 * Se carga una sola vez al arranque. Si el asset no existe o falla el parse,
 * queda vacío y la app sigue funcionando con RadioDb + FastPairModels.
 */
object CatalogStore {

    private const val TAG = "FieldwatchCatalog"
    private const val ASSET = "fieldwatch_catalog.json"

    @Volatile private var ready = false
    private val map = ConcurrentHashMap<String, Entry>(128_000)

    data class Entry(
        val name: String,
        val type: String? = null,
        val category: String? = null,
        val source: String? = null,
    )

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val t0 = System.currentTimeMillis()
            runCatching {
                val text = context.assets.open(ASSET)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val raw = Json { ignoreUnknownKeys = true }
                    .decodeFromString<Map<String, RawEntry>>(text)
                raw.forEach { (k, v) ->
                    if (v.name.isNotBlank()) {
                        map[k] = Entry(v.name, v.type, v.category, v.source)
                    }
                }
                val ms = System.currentTimeMillis() - t0
                Log.i(TAG, "loaded ${map.size} entries from $ASSET in ${ms}ms")
            }.onFailure { err ->
                Log.w(TAG, "could not load $ASSET: ${err.message}")
            }
            ready = true
        }
    }

    fun isReady(): Boolean = ready

    fun size(): Int = map.size

    /** Fast Pair 24-bit model ID. */
    fun fastPairName(modelId: Int): String? =
        map["fastpair:%06X".format(modelId and 0xFFFFFF)]?.name

    /** Samsung model ID (hex string, sin "0x"). */
    fun samsungName(hexCode: String): String? =
        map["samsung:${hexCode.uppercase()}"]?.name

    /** OUI IEEE (24-bit = 6 hex, 28-bit = 7 hex, 36-bit = 9 hex). */
    fun ouiName(ouiHex: String): String? =
        map["oui:${ouiHex.uppercase()}"]?.name

    /** Bluetooth SIG Company ID (16-bit). */
    fun companyName(id: Int): String? =
        map["company:%04X".format(id and 0xFFFF)]?.name

    /** Resuelve primero por OUI más específico (9 > 7 > 6 hex). */
    fun ouiNameLongest(macHex: String): String? {
        val h = macHex.uppercase().replace(":", "").replace("-", "")
        if (h.length < 6) return null
        return when {
            h.length >= 9 -> ouiName(h.take(9)) ?: ouiName(h.take(7)) ?: ouiName(h.take(6))
            h.length >= 7 -> ouiName(h.take(7)) ?: ouiName(h.take(6))
            else -> ouiName(h.take(6))
        }
    }

    @Serializable
    private data class RawEntry(
        val name: String,
        val type: String? = null,
        val category: String? = null,
        val source: String? = null,
    )
}