package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class RBBIDictionaryCacheTest {
    @Test fun dictionaryBoundariesCarryStatusesAndCopiesAreIndependent() {
        val source = "東京都に行きます"
        val text = StringCharacterIterator(source)
        val engine = CjkBreakEngine(false)
        val resolver: (Int) -> LanguageBreakEngine = { engine }
        val cache = RBBIDictionaryCache(text, RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/word.brk")), resolver, false)
        cache.populateDictionary(0, source.length, 12, 34)
        assertTrue(cache.following(0)); assertEquals(34, cache.statusIndex)
        val boundary = cache.boundary
        assertTrue(cache.preceding(boundary)); assertEquals(0, cache.boundary); assertEquals(12, cache.statusIndex)
        val copy = cache.copyFor(text.clone(), resolver)
        cache.reset()
        assertFalse(cache.following(0))
        assertTrue(copy.following(0)); assertEquals(boundary, copy.boundary)
        copy.populateDictionary(0, 1, 99, 100) // Original early return keeps the old cache intact.
        assertTrue(copy.preceding(boundary)); assertEquals(12, copy.statusIndex)
    }
}
