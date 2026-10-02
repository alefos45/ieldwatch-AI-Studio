package app.fieldwatch.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {
    @Test
    fun resolvesExplicitLanguages() {
        assertEquals("es", resolveLanguage("es"))
        assertEquals("es", resolveLanguage("ES"))
        assertEquals("en", resolveLanguage("en"))
        assertEquals("en", resolveLanguage("EN"))
    }

    @Test
    fun autoResolvesToSupportedLanguage() {
        val resolved = resolveLanguage("auto")
        assert(resolved == "en" || resolved == "es")
    }

    @Test
    fun stringsContainValidKeys() {
        assertEquals("En vivo", StringsEs.navLive)
        assertEquals("Live", StringsEn.navLive)
        assertEquals("Ajustes", StringsEs.navSettings)
        assertEquals("Settings", StringsEn.navSettings)
        assertEquals("Búsqueda", StringsEs.huntTitle)
        assertEquals("Hunt", StringsEn.huntTitle)
    }
}
