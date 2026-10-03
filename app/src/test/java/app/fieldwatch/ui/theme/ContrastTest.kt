package app.fieldwatch.ui.theme

import app.fieldwatch.domain.Palette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * FASE 5 (Bloque 2): verifica empíricamente los ratios WCAG 2.x de los
 * colores que cambió el Bloque 2.
 *
 * Fórmula: (Lmax + 0.05) / (Lmin + 0.05) sobre luminancia relativa sRGB.
 *
 * Cubre:
 *  - Palette.fleet: los 9 colores de firma contra el surface del tema Dark.
 *  - DarkOutline (#5E6874) contra background Dark (#0B0F14). AA non-text.
 *  - NightOutline (#944E4E) contra background Night (#0B0808). AA non-text.
 *  - HighContrastDark outline (#6E7886) contra negro puro. AA no-text holgado.
 *
 * Lo que NO verifica:
 *  - No tiene acceso a los ColorScheme de Compose en JVM puro. Si cambias
 *    Theme.kt, revisa que los hex de aquí coincidan con los de allí.
 *  - No verifica contraste de texto normal sobre surface. Ese caso ya cumple
 *    AAA en el theme actual (onSurface ≈ 14:1) y no lo tocamos en este bloque.
 */
class ContrastTest {

    // --- Duplicado del theme. Si cambias Theme.kt, actualiza estos. ---

    /** DarkColors.surface. */
    private val SURFACE_DARK = 0xFF141A22.toInt()

    /** DarkColors.background. */
    private val BACKGROUND_DARK = 0xFF0B0F14.toInt()

    /** NightColors.background. */
    private val BACKGROUND_NIGHT = 0xFF0B0808.toInt()

    /** HighContrastDarkColors.background. */
    private val HC_BLACK = 0xFF000000.toInt()

    /** FASE 5 Bloque 2: Theme.kt DarkOutline. */
    private val OUTLINE_DARK = 0xFF5E6874.toInt()

    /** FASE 5 Bloque 2: Theme.kt NightOutline. */
    private val OUTLINE_NIGHT = 0xFF944E4E.toInt()

    /** FASE 5 Bloque 2: Theme.kt HighContrastDark outline. */
    private val OUTLINE_HC_DARK = 0xFF6E7886.toInt()

    // --- Helpers WCAG ---

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(fg: Int, bg: Int): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun hex(argb: Int): String = "%08X".format(argb)

    private fun fmt(r: Double): String = "%.2f:1".format(r)

    // --- Tests ---

    @Test
    fun paletteFleetPassesAaTextOnDarkSurface() {
        // Los chips de firma usan el color de Palette.fleet como color de
        // texto del nombre. Mínimo WCAG AA texto = 4.5:1.
        Palette.fleet.forEachIndexed { i, argb ->
            val r = contrast(argb, SURFACE_DARK)
            assertTrue(
                "Palette.fleet[$i] = ${hex(argb)} da ${fmt(r)} sobre surface " +
                    "${hex(SURFACE_DARK)}. Mínimo AA texto es 4.5:1.",
                r >= 4.5,
            )
        }
    }

    @Test
    fun outlineDarkPassesNonTextOnBackground() {
        // outline se usa para bordes de OutlinedTextField, Divider, y todos
        // los bordes derivados (spectreTileEdge). WCAG AA non-text = 3:1.
        val r = contrast(OUTLINE_DARK, BACKGROUND_DARK)
        assertTrue(
            "Theme.DarkOutline ${hex(OUTLINE_DARK)} da ${fmt(r)} sobre " +
                "background ${hex(BACKGROUND_DARK)}. Mínimo non-text es 3:1.",
            r >= 3.0,
        )
    }

    @Test
    fun outlineNightPassesNonTextOnNightBackground() {
        val r = contrast(OUTLINE_NIGHT, BACKGROUND_NIGHT)
        assertTrue(
            "Theme.NightOutline ${hex(OUTLINE_NIGHT)} da ${fmt(r)} sobre " +
                "background ${hex(BACKGROUND_NIGHT)}. Mínimo non-text es 3:1.",
            r >= 3.0,
        )
    }

    @Test
    fun outlineHighContrastDarkPassesAaNonTextOnBlack() {
        // High contrast busca margen holgado para sol directo. Exigimos
        // 4.5:1 (mismo umbral que texto normal), no el mínimo 3:1.
        val r = contrast(OUTLINE_HC_DARK, HC_BLACK)
        assertTrue(
            "HighContrastDark outline ${hex(OUTLINE_HC_DARK)} da ${fmt(r)} " +
                "sobre negro puro. Mínimo en HC es 4.5:1.",
            r >= 4.5,
        )
    }

    @Test
    fun paletteFleetIndexSevenWasTheFix() {
        // Regresión concreta: el índice 7 cambió de #3D8B6E (4.26:1) a
        // #52B896 (7.20:1). Si alguien lo revierte, este test lo pilla.
        val idx7 = Palette.fleet[7]
        val r = contrast(idx7, SURFACE_DARK)
        assertTrue(
            "Palette.fleet[7] = ${hex(idx7)} da ${fmt(r)}. " +
                "El color corregido es 0xFF52B896 (7.20:1). " +
                "Si revertiste a 0xFF3D8B6E, vuelve a aplicar el fix de Fase 5.",
            r >= 4.5,
        )
    }
}