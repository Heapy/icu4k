package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNameCompositionTest {
    @Test fun standardDialectKeywordAndMissingNames() {
        val standard = LocaleDisplayNameComponents(ULocale("en"))
        assertEquals("English (United States)", standard.localeDisplayName("en_US"))
        assertEquals("Unknown language", standard.localeDisplayName("root"))
        assertEquals("American English", LocaleDisplayNameComponents(ULocale("en"), DisplayContext.DIALECT_NAMES).localeDisplayName("en_US"))
        assertEquals("English (UK)", LocaleDisplayNameComponents(ULocale("en"), DisplayContext.LENGTH_SHORT).localeDisplayName("en_GB"))
        assertEquals("English (foo=bar)", standard.localeDisplayName("en@foo=bar"))
        val strict = LocaleDisplayNameComponents(ULocale("en"), DisplayContext.NO_SUBSTITUTE)
        assertNull(strict.localeDisplayName("zz_XX")); assertNull(strict.localeDisplayName("en@foo=bar"))
        assertEquals("Anglais (États-Unis)", LocaleDisplayNameComponents(ULocale("fr"), DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE).localeDisplayName("en_US"))
        assertEquals(standard.localeDisplayName("zh_Hant_TW"), standard.localeDisplayName(ULocale("zh_Hant_TW")))
    }
}
