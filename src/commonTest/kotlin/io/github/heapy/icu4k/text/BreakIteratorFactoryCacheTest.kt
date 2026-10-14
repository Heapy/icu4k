@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.BreakIteratorFactoryCache
import io.github.heapy.icu4k.impl.CacheValue
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class BreakIteratorFactoryCacheTest {
    @Test fun cacheCopiesBeforeReturningAndSeparatesKinds() {
        val wasStrong = CacheValue.futureInstancesWillBeStrong()
        CacheValue.setStrength(CacheValue.Strength.STRONG)
        try {
            var creations = 0
            val cache = BreakIteratorFactoryCache { _, _ -> creations++; RuleBasedBreakIterator(".;") }
            val en = ULocale("en")
            val first = cache.get(en, 0); first.setText("abc"); first.next()
            val second = cache.get(ULocale("en"), 0)
            assertNotSame(first, second); assertEquals(0, second.getText().getEndIndex()); assertEquals(1, creations)
            cache.get(en, 1); assertEquals(2, creations)
            cache.clearMatching(ULocale("fr"), 0); cache.get(en, 0); assertEquals(2, creations)
            cache.clearMatching(en, 0); cache.get(en, 0); assertEquals(3, creations)
            cache.get(ULocale("fr"), 0); cache.get(en, 0); assertEquals(5, creations)
            cache.clear(); cache.get(en, 0); assertEquals(6, creations)
            assertEquals("Specified locale is null", assertFailsWith<NullPointerException> { cache.get(null, 99) }.message)
            assertFailsWith<IndexOutOfBoundsException> { cache.get(en, 99) }
        } finally { CacheValue.setStrength(if (wasStrong) CacheValue.Strength.STRONG else CacheValue.Strength.SOFT) }
    }
    @Test fun publicFactoriesReturnIndependentResourceIterators() {
        val first = BreakIterator.getSentenceInstance(ULocale("en")); first.setText("Hello. Next.")
        assertEquals(7, first.next())
        val second = BreakIterator.getSentenceInstance(ULocale("en"))
        assertNotSame(first, second); assertEquals(0, second.getText().getEndIndex())
        second.setText("Different."); assertEquals(7, first.current())
        assertFailsWith<NullPointerException> { BreakIterator.getWordInstance(null) }
    }
}
