package app.fieldwatch.ui.a11y

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * FASE 5 (Bloque 1): snapshot inmutable de las preferencias de accesibilidad
 * del operador. Se deriva de [app.fieldwatch.domain.AppSettings] en el
 * ViewModel y se propaga a la UI por [LocalA11yState].
 *
 * Se mantiene separado de AppSettings para que las pantallas no dependan
 * de todo el modelo de ajustes. Añadir un campo aquí implica:
 *  1. Añadirlo también a AppSettings con default.
 *  2. Extender el map en FieldwatchViewModel.a11yState.
 *  3. Añadir el switch en SettingsScreen (Bloque correspondiente).
 */
data class A11yState(
    /**
     * Modo de alto contraste. Refuerza el contraste del texto crítico y los
     * chips para uso bajo sol directo. NO cambia la paleta base.
     * Bloque 2 lo consume.
     */
    val highContrast: Boolean = false,
    /**
     * Reduce animaciones decorativas (radar sweep, flashes, transiciones).
     * No afecta navegación ni animaciones funcionales cortas.
     * Bloque 7 lo consume.
     */
    val reduceMotion: Boolean = false,
    /**
     * Reservado para ampliar áreas táctiles más allá de 48dp donde aplique.
     * En Bloque 1 solo se persiste; Bloques 3+ deciden cómo consumirlo.
     */
    val largeTouch: Boolean = false,
) {
    companion object {
        val Default: A11yState = A11yState()
    }
}

/**
 * CompositionLocal con las preferencias de accesibilidad activas.
 * Se provee una sola vez en [app.fieldwatch.ui.FieldwatchRoot].
 * Usar `staticCompositionLocalOf` porque los cambios son raros y, cuando
 * ocurren, se quiere recomponer todo el subárbol de UI.
 */
val LocalA11yState = staticCompositionLocalOf { A11yState.Default }