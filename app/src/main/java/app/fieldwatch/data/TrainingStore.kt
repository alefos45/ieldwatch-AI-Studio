package app.fieldwatch.data

import android.content.Context
import app.fieldwatch.domain.BehavioralClass
import app.fieldwatch.domain.BehavioralKind
import app.fieldwatch.domain.BehaviorFeatures
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TrainingFeatures
import app.fieldwatch.domain.TrainingLabel
import app.fieldwatch.domain.TrainingSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Persiste muestras de entrenamiento en JSONL. Un archivo por día UTC:
 *   filesDir/collection/features-YYYYMMDD.jsonl
 *
 * Reglas de captura:
 *  - Una muestra por deviceKey único en esta sesión de app (evita duplicados
 *    del mismo radio visto 200 veces en 10 minutos).
 *  - Muestras arrancan con label = null. El operador las etiqueta en el detalle.
 *  - El MAC crudo NUNCA se guarda. Solo un hash de 16 hex.
 *  - El dataset de export incluye TODAS las muestras (etiquetadas o no);
 *    el filtro de labels se hace en el script Python.
 */
class TrainingStore(context: Context) {

    private val dir = File(context.filesDir, "collection").apply { mkdirs() }
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** Cache en memoria de qué devices ya escribimos hoy (por deviceHash). */
    private val sessionSeen = HashSet<String>(512)

    /** Buffer de muestras vivas para la UI (etiquetas recientes). */
    @Volatile private var liveIndex: Map<String, TrainingSample> = emptyMap()

    private fun todayFile(): File {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return File(dir, "features-${fmt.format(Date())}.jsonl")
    }

    fun deviceHash(device: Sighting): String = hashKey("${device.kind.name}:${device.mac}")

    private fun hashKey(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Escribe una muestra si el device no se ha visto hoy.
     * Idempotente dentro de la sesión de app (usa deviceHash en memoria).
     * Nunca lanza: si falla, se ignora silenciosamente para no romper la captura.
     */
    suspend fun record(
        device: Sighting,
        features: BehaviorFeatures,
        heuristic: BehavioralClass,
    ): Boolean {
        val hash = deviceHash(device)
        if (!sessionSeen.add(hash)) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val sample = TrainingSample(
                        id = UUID.randomUUID().toString(),
                        at = device.firstSeen,
                        radioKind = device.kind.name,
                        deviceHash = hash,
                        features = TrainingFeatures.from(features),
                        heuristicKind = heuristic.kind.takeIf { it != BehavioralKind.UNKNOWN }?.name,
                        heuristicConfidence = heuristic.confidence.takeIf { it > 0f },
                        heuristicReasons = heuristic.because,
                    )
                    appendLine(sample)
                    liveIndex = liveIndex + (hash to sample)
                    true
                }.getOrDefault(false)
            }
        }
    }

    /**
     * Igual que [record] pero ignora el filtro de sesión. Se usa cuando el
     * operador etiqueta manualmente un radio: queremos garantizar que exista
     * la fila aunque no se haya capturado pasivamente.
     */
    suspend fun recordForced(
        device: Sighting,
        features: BehaviorFeatures,
        heuristic: BehavioralClass,
    ): Boolean {
        val hash = deviceHash(device)
        sessionSeen.add(hash)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val existing = readAllLocked().any { it.deviceHash == hash }
                    if (existing) return@runCatching false
                    val sample = TrainingSample(
                        id = UUID.randomUUID().toString(),
                        at = device.firstSeen,
                        radioKind = device.kind.name,
                        deviceHash = hash,
                        features = TrainingFeatures.from(features),
                        heuristicKind = heuristic.kind.takeIf { it != BehavioralKind.UNKNOWN }?.name,
                        heuristicConfidence = heuristic.confidence.takeIf { it > 0f },
                        heuristicReasons = heuristic.because,
                    )
                    appendLine(sample)
                    liveIndex = liveIndex + (hash to sample)
                    true
                }.getOrDefault(false)
            }
        }
    }

    /** Marca una muestra como etiquetada. Reescribe el archivo si es necesario. */
    suspend fun label(
        deviceHash: String,
        label: TrainingLabel,
        notes: String? = null,
    ): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val all = readAllLocked()
                var changed = false
                val updated = all.map { row ->
                    if (row.deviceHash == deviceHash && row.label == null) {
                        changed = true
                        row.copy(
                            label = label.name,
                            labeledAt = System.currentTimeMillis(),
                            notes = notes?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        row
                    }
                }
                if (changed) rewriteAllLocked(updated)
                changed
            }.getOrDefault(false)
        }
    }

    /** Devuelve la etiqueta actual de un deviceHash, o null. */
    suspend fun labelFor(deviceHash: String): TrainingLabel? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readAllLocked()
                .firstOrNull { it.deviceHash == deviceHash && it.label != null }
                ?.label
                ?.let { runCatching { TrainingLabel.valueOf(it) }.getOrNull() }
        }
    }

    /** Muestras para las que este deviceHash ya tiene label asignada. */
    suspend fun hasLabel(deviceHash: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            readAllLocked().any { it.deviceHash == deviceHash && it.label != null }
        }
    }

    /** Borra la etiqueta de una fila sin borrar la fila. Útil para re-etiquetar. */
    suspend fun clearLabel(deviceHash: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val all = readAllLocked()
                var changed = false
                val updated = all.map { row ->
                    if (row.deviceHash == deviceHash && row.label != null) {
                        changed = true
                        row.copy(label = null, labeledAt = null, notes = null)
                    } else {
                        row
                    }
                }
                if (changed) rewriteAllLocked(updated)
                changed
            }.getOrDefault(false)
        }
    }

    suspend fun readAll(): List<TrainingSample> = mutex.withLock {
        withContext(Dispatchers.IO) { readAllLocked() }
    }

    /** Solo muestras con etiqueta válida (excluye UNSURE y null). */
    suspend fun readLabeled(): List<TrainingSample> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readAllLocked().filter {
                it.label != null && it.label != TrainingLabel.UNSURE.name
            }
        }
    }

    suspend fun exportCombined(): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readAllLocked()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(Date())
            val out = File(dir, "fieldwatch-training-$stamp.jsonl")
            out.outputStream().buffered(64 * 1024).use { raw ->
                OutputStreamWriter(raw, Charsets.UTF_8).use { w ->
                    for (row in all) {
                        w.write(json.encodeToString(TrainingSample.serializer(), row))
                        w.write("\n")
                    }
                }
            }
            out
        }
    }

    /** Cuenta total y cuántas tienen etiqueta válida. */
    suspend fun counts(): Pair<Int, Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readAllLocked()
            val labeled = all.count {
                it.label != null && it.label != TrainingLabel.UNSURE.name
            }
            all.size to labeled
        }
    }

    suspend fun clear(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val count = readAllLocked().size
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            sessionSeen.clear()
            liveIndex = emptyMap()
            count
        }
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun appendLine(sample: TrainingSample) {
        val line = json.encodeToString(TrainingSample.serializer(), sample) + "\n"
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(todayFile(), true), Charsets.UTF_8),
            32 * 1024,
        ).use { it.write(line) }
    }

    private fun readAllLocked(): List<TrainingSample> {
        val out = ArrayList<TrainingSample>(512)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("features-") && it.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEach
                        runCatching {
                            json.decodeFromString(TrainingSample.serializer(), line)
                        }.onSuccess { out += it }
                    }
                }
            }
        return out
    }

    private fun rewriteAllLocked(rows: List<TrainingSample>) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("features-") && it.name.endsWith(".jsonl") }
            ?.forEach { runCatching { it.delete() } }
        val byDay = rows.groupBy { row ->
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.format(Date(row.at))
        }
        for ((day, dayRows) in byDay) {
            val file = File(dir, "features-$day.jsonl")
            file.outputStream().buffered(32 * 1024).use { raw ->
                OutputStreamWriter(raw, Charsets.UTF_8).use { w ->
                    for (row in dayRows) {
                        w.write(json.encodeToString(TrainingSample.serializer(), row))
                        w.write("\n")
                    }
                }
            }
        }
    }
}