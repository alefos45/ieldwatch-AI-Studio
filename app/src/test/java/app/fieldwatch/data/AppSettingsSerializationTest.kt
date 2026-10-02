package app.fieldwatch.data

import app.fieldwatch.domain.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 5 (Bloque 1): verifica que las prefs de accesibilidad nuevas
 * (a11yHighContrast, a11yReduceMotion, a11yLargeTouch) sobreviven el
 * round-trip JSON y que un config.json previo a Fase 5 decodifica con
 * los defaults.
 *
 * No cubre la persistencia real a disco (eso es ConfigStore + IO).
 * Solo garantiza que el contrato de serialización está intacto.
 */
class AppSettingsSerializationTest {

    /** Misma configuración que ConfigStore: encodeDefaults + ignoreUnknownKeys. */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun a11yDefaultsAreFalse() {
        val settings = AppSettings()
        assertFalse("a11yHighContrast debe arrancar en false", settings.a11yHighContrast)
        assertFalse("a11yReduceMotion debe arrancar en false", settings.a11yReduceMotion)
        assertFalse("a11yLargeTouch debe arrancar en false", settings.a11yLargeTouch)
    }

    @Test
    fun a11yFieldsRoundTrip() {
        val original = AppSettings(
            a11yHighContrast = true,
            a11yReduceMotion = true,
            a11yLargeTouch = true,
        )
        val text = json.encodeToString(original)
        val decoded = json.decodeFromString<AppSettings>(text)
        assertTrue(decoded.a11yHighContrast)
        assertTrue(decoded.a11yReduceMotion)
        assertTrue(decoded.a11yLargeTouch)
    }

    @Test
    fun a11yFieldsEncodedWhenChangedFromDefault() {
        // encodeDefaults = true en ConfigStore ⇒ las claves deben estar
        // presentes en el JSON cuando el operador las activa.
        val text = json.encodeToString(AppSettings(a11yHighContrast = true))
        assertTrue(
            "a11yHighContrast debe aparecer en el JSON al activarse",
            text.contains("a11yHighContrast"),
        )
        assertTrue(text.contains("a11yReduceMotion"))
        assertTrue(text.contains("a11yLargeTouch"))
    }

    @Test
    fun legacyJsonWithoutA11yFieldsDecodesToDefaults() {
        // Simula un config.json generado antes de Fase 5: sin las tres claves
        // nuevas. ignoreUnknownKeys + defaults ⇒ debe decodificar sin error
        // y dejar los campos nuevos en false.
        val legacy = """{"language":"es","darkTheme":true,"nightMode":true}"""
        val decoded = json.decodeFromString<AppSettings>(legacy)
        assertEquals("es", decoded.language)
        assertTrue(decoded.darkTheme)
        assertTrue(decoded.nightMode)
        assertFalse(decoded.a11yHighContrast)
        assertFalse(decoded.a11yReduceMotion)
        assertFalse(decoded.a11yLargeTouch)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        // Protección contra un pack de ajustes importado desde una versión
        // futura con más claves. No debe romper la decodificación.
        val future = """{"language":"en","a11yFutureThing":123}"""
        val decoded = json.decodeFromString<AppSettings>(future)
        assertEquals("en", decoded.language)
        assertFalse(decoded.a11yHighContrast)
    }
}