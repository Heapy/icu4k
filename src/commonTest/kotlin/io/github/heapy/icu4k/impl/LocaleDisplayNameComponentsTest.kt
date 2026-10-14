package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNameComponentsTest {
    @Test fun localizedComponentsSubstitutionAndCapitalization() {
        val en = LocaleDisplayNameComponents(ULocale("en"))
        assertEquals("German", en.languageDisplayName("de"))
        assertEquals("Latin", en.scriptDisplayName("Latn"))
        assertEquals("United States", en.regionDisplayName("US"))
        assertEquals("root", en.languageDisplayName("root"))
        assertNull(LocaleDisplayNameComponents(ULocale("en"), DisplayContext.NO_SUBSTITUTE).languageDisplayName("root"))
        val fr = LocaleDisplayNameComponents(ULocale("fr"), DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        assertEquals("Anglais", fr.languageDisplayName("en"))
        assertEquals("États-Unis", fr.regionDisplayName("US"))
        val short = LocaleDisplayNameComponents(ULocale("en"), DisplayContext.LENGTH_SHORT)
        assertEquals("United States", short.regionDisplayName("US"))
        assertEquals("UK", short.regionDisplayName("GB"))
        val options = LocaleDisplayNameComponents(ULocale("en"), DisplayContext.LENGTH_SHORT, DisplayContext.LENGTH_FULL)
        assertEquals(DisplayContext.LENGTH_FULL, options.getContext(DisplayContext.Type.DISPLAY_LENGTH))
        assertEquals("unknown", en.keyValueDisplayName("currency", "unknown"))
    }
}
