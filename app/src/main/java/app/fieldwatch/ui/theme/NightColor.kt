package app.fieldwatch.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val LocalNightMode = staticCompositionLocalOf { false }

/**
 * Map any sRGB color to a red luminance ramp (cockpit / field night display).
 *
 * FASE 5 (Bloque 5): antes esta función colapsaba los tres colores más
 * distintos (Phosphor y≈0.78, Amber y≈0.68, SignalRed y≈0.34) a rojos
 * casi idénticos. Un usuario con deuteranopía no los distinguía.
 *
 * Ahora se cuantiza la luminancia en 4 bandas y cada banda mapea a un
 * rojo con separación real. Los chips de firma siguen llevando icono
 * (`ClassGlyphs`), así que el color no es la única señal — pero al menos
 * los tonos se diferencian entre sí.
 */
fun nightForegroundArgb(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val y = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

    // 4 bandas de luminancia → 4 niveles de rojo.
    // Banda 0 (y < 0.25): rojo apagado — SignalRed, teal.
    // Banda 1 (0.25–0.50): rojo medio-bajo — Cyan, violeta.
    // Banda 2 (0.50–0.70): rojo medio-alto — Amber, gris claro.
    // Banda 3 (y ≥ 0.70): rojo vivo — Phosphor, blanco.
    val band = when {
        y < 0.25f -> 0
        y < 0.50f -> 1
        y < 0.70f -> 2
        else -> 3
    }
    val (nr, ng, nb) = when (band) {
        0 -> Triple(0.48f, 0.06f, 0.06f)
        1 -> Triple(0.62f, 0.10f, 0.10f)
        2 -> Triple(0.78f, 0.16f, 0.16f)
        else -> Triple(0.95f, 0.26f, 0.26f)
    }
    return (a shl 24) or
        (((nr * 255f).toInt() and 0xFF) shl 16) or
        (((ng * 255f).toInt() and 0xFF) shl 8) or
        ((nb * 255f).toInt() and 0xFF)
}

fun Color.asNightForeground(): Color = Color(nightForegroundArgb(toArgb()))

/** Identity when [night] is false — day/dark colors are unchanged. */
fun Color.nightIf(night: Boolean): Color = if (night) asNightForeground() else this