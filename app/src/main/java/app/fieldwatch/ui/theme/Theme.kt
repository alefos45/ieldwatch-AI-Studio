package app.fieldwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Phosphor = Color(0xFF3DFF9A)
/** Checked switch / slider fill — same hue as Phosphor, less neon. */
val PhosphorActive = Color(0xFF35D683)
val Amber = Color(0xFFFFB020)
val SignalRed = Color(0xFFFF3D5A)
val Cyan = Color(0xFF4FC3F7)
val Night = Color(0xFF0B0F14)
val Panel = Color(0xFF141A22)
val Panel2 = Color(0xFF1B232D)

/**
 * FASE 5 (Bloque 2): outline subido de #2A3340 (1.44:1) a #5E6874 (3.51:1
 * sobre background) para cumplir WCAG AA non-text. Se aplica a Divider,
 * OutlinedTextField y todos los bordes derivados (spectreTileEdge).
 */
private val DarkOutline = Color(0xFF5E6874)

/**
 * FASE 5 (Bloque 2): outline night subido de #5A3030 (1.78:1) a #944E4E
 * (3.37:1 sobre fondo #0B0808). Mismo rol que DarkOutline pero en rojo.
 */
private val NightOutline = Color(0xFF944E4E)

private val DarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF163326),
    onPrimaryContainer = Phosphor,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Cyan,
    background = Night,
    onBackground = Color(0xFFD5DCE3),
    surface = Panel,
    onSurface = Color(0xFFD5DCE3),
    surfaceVariant = Panel2,
    onSurfaceVariant = Color(0xFF9AA6B2),
    outline = DarkOutline,
    error = SignalRed,
)

/**
 * Red-on-black field display. Background stays dark; chrome and accents
 * are red ramps. Used only while Settings → Night mode is on.
 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF5A5A),
    onPrimary = Color(0xFF2A0808),
    primaryContainer = Color(0xFF3A1212),
    onPrimaryContainer = Color(0xFFFF8A8A),
    secondary = Color(0xFFE07070),
    onSecondary = Color(0xFF2A0808),
    tertiary = Color(0xFFCC6666),
    background = Color(0xFF0B0808),
    onBackground = Color(0xFFFFC9C9),
    surface = Color(0xFF161010),
    onSurface = Color(0xFFFFC9C9),
    surfaceVariant = Color(0xFF1E1414),
    onSurfaceVariant = Color(0xFFC48A8A),
    outline = NightOutline,
    error = Color(0xFFFF7A7A),
)

/**
 * FASE 5 (Bloque 2): esquema de alto contraste para uso bajo sol directo.
 * Se activa con Settings → Apariencia → Alto contraste (a11yHighContrast).
 *
 * Objetivos:
 *  - Texto en cualquier superficie: ≥ 12:1 (AAA holgado)
 *  - Non-text (outline, bordes): ≥ 4.5:1
 *  - background vs surface: separación visible
 *
 * Diferencias frente a DarkColors:
 *  - background pasa a negro puro
 *  - surface / surfaceVariant suben
 *  - onSurfaceVariant pasa a #C8D2DC (10.6:1 sobre surface)
 *  - outline pasa a #6E7886 (4.86:1 sobre fondo negro)
 *  - onSurface pasa a #E8EEF2
 */
private val HighContrastDarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF163326),
    onPrimaryContainer = Phosphor,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Cyan,
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF1A2029),
    onSurface = Color(0xFFE8EEF2),
    surfaceVariant = Color(0xFF242C38),
    onSurfaceVariant = Color(0xFFC8D2DC),
    outline = Color(0xFF6E7886),
    error = SignalRed,
)

/**
 * FASE 5 (Bloque 2): alto contraste + modo nocturno. Rojo sobre negro puro.
 * Sube onSurfaceVariant y outline respecto a NightColors normal.
 */
private val HighContrastNightColors = darkColorScheme(
    primary = Color(0xFFFF7070),
    onPrimary = Color(0xFF2A0808),
    primaryContainer = Color(0xFF3A1212),
    onPrimaryContainer = Color(0xFFFF9A9A),
    secondary = Color(0xFFE88888),
    onSecondary = Color(0xFF2A0808),
    tertiary = Color(0xFFD47A7A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFD8D8),
    surface = Color(0xFF1A0E0E),
    onSurface = Color(0xFFFFD8D8),
    surfaceVariant = Color(0xFF261616),
    onSurfaceVariant = Color(0xFFE0A8A8),
    outline = Color(0xFFA55A5A),
    error = Color(0xFFFF9090),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A48),
    onPrimary = Color.White,
    secondary = Color(0xFF9A6400),
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF12171C),
    surface = Color.White,
    onSurface = Color(0xFF12171C),
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = Color(0xFF3F4A55),
    outline = Color(0xFFC5CDD4),
    error = Color(0xFFB00020),
)

val Mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.3.sp,
)

/**
 * FASE 5 (Bloque 2): `a11yHighContrast` selecciona esquemas con outline y
 * onSurfaceVariant subidos para uso bajo sol directo. Es independiente de
 * `nightMode` — las dos combinaciones están definidas.
 */
@Composable
fun FieldwatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    nightMode: Boolean = false,
    a11yHighContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        a11yHighContrast && nightMode -> HighContrastNightColors
        a11yHighContrast -> HighContrastDarkColors
        nightMode -> NightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalNightMode provides nightMode) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}