package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.LocaleDisplayNamesImpl
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNamesTest {
    @Test fun publicNamesContextsAndSharedLastEntryCache() {
        val en = ULocale("en")
        val a = LocaleDisplayNames.getInstance(en)
        assertEquals("English (United States)", a.localeDisplayName("en_US"))
        assertSame(a, LocaleDisplayNames.getInstance(ULocale("en"), *emptyArray<DisplayContext>()))
        assertSame(a, LocaleDisplayNames.getInstance(en, DisplayContext.LENGTH_SHORT, DisplayContext.LENGTH_FULL))
        assertEquals("American English", LocaleDisplayNames.getInstance(en, LocaleDisplayNames.DialectHandling.DIALECT_NAMES).localeDisplayName("en_US"))
        assertNotSame(a, LocaleDisplayNames.getInstance(en))
        assertTrue(LocaleDisplayNamesImpl.haveData(LocaleDisplayNamesImpl.DataTableType.LANG))
        assertTrue(LocaleDisplayNamesImpl.haveData(LocaleDisplayNamesImpl.DataTableType.REGION))
    }
    @Test fun failingConstructionFallsBackToCodeNames() {
        val fallback = LocaleDisplayNames.getInstance(null as ULocale?, DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        assertNull(fallback.getLocale()); assertEquals("en_US", fallback.localeDisplayName("en_US"))
        assertEquals("currency", fallback.keyDisplayName("currency"))
        assertEquals(DisplayContext.STANDARD_NAMES, fallback.getContext(DisplayContext.Type.DISPLAY_LENGTH))
        assertEquals(DisplayContext.CAPITALIZATION_FOR_STANDALONE, fallback.getContext(DisplayContext.Type.CAPITALIZATION))
        val rows = fallback.getUiListCompareWholeItems(null, null)
        assertTrue(rows.isEmpty()); assertFailsWith<UnsupportedOperationException> { (rows as MutableList<LocaleDisplayNames.UiListItem>).add(LocaleDisplayNames.UiListItem(null, null, null, null)) }
    }
}
