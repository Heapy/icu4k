package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.BreakIteratorDataFactory
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.test.*

class BreakIteratorDataFactoryTest {
    @Test fun resourceRulesMetadataAndClone() {
        val iterator = BreakIteratorDataFactory.create(ULocale("en_US"), BreakIterator.KIND_SENTENCE)
        assertEquals("en_US", iterator.getLocale(ULocale.VALID_LOCALE)?.getName())
        iterator.setText("Hello. Next.")
        val copy = iterator.clone()
        assertEquals(iterator.getLocale(ULocale.ACTUAL_LOCALE), copy.getLocale(ULocale.ACTUAL_LOCALE))
        assertEquals(7, copy.next()); assertEquals(0, iterator.current())
        assertFailsWith<IllegalArgumentException> { iterator.setLocale(null, ULocale.ROOT) }
        val filtered = BreakIteratorDataFactory.create(ULocale("en@ss=standard"), BreakIterator.KIND_SENTENCE)
        assertNull(filtered.getLocale(ULocale.ACTUAL_LOCALE))
        filtered.setText("Mr. Smith. Next."); assertEquals(11, filtered.next())
        assertFailsWith<MissingResourceException> { BreakIteratorDataFactory.create(ULocale.ROOT, 99) }
    }
}
