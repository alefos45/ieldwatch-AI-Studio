package app.fieldwatch.domain

/**
 * Detecta rotación de MAC: mismo nombre anunciado por varios MACs
 * distintos en una ventana temporal corta. Es la firma de un AirTag,
 * SmartTag o cualquier advertiser que cambia de dirección para evitar
 * tracking — el nombre sobrevive, el MAC rota.
 *
 * No hace estado propio: se calcula on-demand desde el listado actual.
 */
object RotationDetector {
    const val WINDOW_MS = 10 * 60_000L

    /**
     * Cuántos MACs distintos (excluyendo el propio) anuncian el mismo nombre
     * en la ventana. 0 = nombre único. >= 2 = hay rotación clara.
     */
    fun rotatingCount(
        device: Sighting,
        all: List<Sighting>,
        now: Long = System.currentTimeMillis(),
        windowMs: Long = WINDOW_MS,
    ): Int {
        val name = device.name.trim()
        if (name.isEmpty()) return 0
        if (name.equals(device.mac, ignoreCase = true)) return 0
        val cutoff = now - windowMs
        return all.count { other ->
            other.kind == device.kind &&
                other.key != device.key &&
                other.name.trim() == name &&
                other.lastSeen >= cutoff
        }
    }
}