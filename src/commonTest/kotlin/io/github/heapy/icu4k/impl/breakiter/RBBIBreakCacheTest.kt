package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.impl.RBBIRuleEngine
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class RBBIBreakCacheTest {
    @Test fun circularCacheRefillsInBothDirectionsAndSeeksRemotely() {
        val text = StringCharacterIterator("a".repeat(400))
        val engine = RBBIRuleEngine(RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/char.brk")), text)
        val cache = RBBIBreakCache(engine, RBBIDictionaryCache(text, engine.data, { null }, false))
        repeat(400) { cache.next(); assertEquals(it + 1, engine.position) }
        cache.next(); assertTrue(engine.done)
        cache.following(10); assertEquals(11, engine.position); assertFalse(engine.done)
        cache.preceding(390); assertEquals(389, engine.position)
        repeat(389) { cache.previous() }
        assertEquals(0, engine.position)
        cache.previous(); assertTrue(engine.done)
    }
}
