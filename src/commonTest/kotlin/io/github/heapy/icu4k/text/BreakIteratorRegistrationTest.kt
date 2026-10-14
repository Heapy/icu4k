package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class BreakIteratorRegistrationTest {
    @Test fun registeredInstanceIsClearedAndCachedAsPrototype() {
        val locale = ULocale("zz_ZZ")
        val iterator = RuleBasedBreakIterator("!!forward; .;")
        iterator.setText("before")
        val token = BreakIterator.registerInstance(iterator, locale, BreakIterator.KIND_WORD)
        try {
            assertEquals(0, iterator.getText().getEndIndex())
            assertTrue(locale in BreakIterator.getAvailableULocales())
            val first = BreakIterator.getWordInstance(locale)
            assertSame(iterator, first)
            assertEquals(locale, first.getLocale(ULocale.ACTUAL_LOCALE))
            first.setText("changed")
            val second = BreakIterator.getWordInstance(locale)
            assertNotSame(first, second)
            assertEquals(0, second.getText().getEndIndex())
            assertEquals(locale, second.getLocale(ULocale.VALID_LOCALE))
        } finally { assertTrue(BreakIterator.unregister(token)) }
        assertFalse(BreakIterator.unregister(token))
        assertFalse(locale in BreakIterator.getAvailableULocales())
        assertNotSame(iterator, BreakIterator.getWordInstance(locale))
    }

    @Test fun inheritedLocaleCacheIsRetainedUntilUnregistration() {
        val parent = ULocale("qq")
        val child = ULocale("qq_QQ")
        val before = BreakIterator.getWordInstance(child)
        val registered = RuleBasedBreakIterator("!!forward; .;")
        val token = BreakIterator.registerInstance(registered, parent, BreakIterator.KIND_WORD)
        try {
            // Registration only clears a cache entry whose requested locale equals the registration locale.
            val cached = BreakIterator.getWordInstance(child)
            assertEquals(before.getLocale(ULocale.ACTUAL_LOCALE), cached.getLocale(ULocale.ACTUAL_LOCALE))
            assertSame(registered, BreakIterator.getWordInstance(parent))
            assertSame(registered, BreakIterator.getWordInstance(child))
            assertEquals(parent, registered.getLocale(ULocale.ACTUAL_LOCALE))
        } finally { BreakIterator.unregister(token) }
        assertFailsWith<IllegalArgumentException> { BreakIterator.unregister(null) }
    }
}
