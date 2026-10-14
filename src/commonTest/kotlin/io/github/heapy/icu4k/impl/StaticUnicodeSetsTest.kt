package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class StaticUnicodeSetsTest {
    @Test fun frozenSetsContainPinnedParseSymbols() {
        for (key in StaticUnicodeSets.Key.entries) {
            val set = StaticUnicodeSets.get(key)
            assertTrue(set.isFrozen(), key.name)
            assertSame(set, StaticUnicodeSets.get(key))
            assertFailsWith<UnsupportedOperationException> { set.add('x'.code) }
        }
        assertSame(UnicodeSet.EMPTY, StaticUnicodeSets.get(null))
        assertNotSame(UnicodeSet.EMPTY, StaticUnicodeSets.get(StaticUnicodeSets.Key.EMPTY))
        assertEquals(StaticUnicodeSets.Key.COMMA, StaticUnicodeSets.chooseFrom(",", StaticUnicodeSets.Key.COMMA, StaticUnicodeSets.Key.PERIOD))
        assertEquals(StaticUnicodeSets.Key.PERIOD, StaticUnicodeSets.chooseFrom(".", StaticUnicodeSets.Key.COMMA, StaticUnicodeSets.Key.PERIOD))
        assertEquals(StaticUnicodeSets.Key.DOLLAR_SIGN, StaticUnicodeSets.chooseCurrency("$"))
        assertEquals(StaticUnicodeSets.Key.RUPEE_SIGN, StaticUnicodeSets.chooseCurrency("₹"))
        assertNull(StaticUnicodeSets.chooseCurrency("unsupported"))
        assertTrue(StaticUnicodeSets.get(StaticUnicodeSets.Key.DIGITS).contains(0x0661))
    }
    @Test fun trimSetRetainsUpstreamPropertyChoice() {
        val normal = StaticUnicodeSets.get(StaticUnicodeSets.Key.DEFAULT_IGNORABLES)
        val strict = StaticUnicodeSets.get(StaticUnicodeSets.Key.STRICT_IGNORABLES)
        assertTrue(normal.contains(' '.code))
        assertTrue(normal.contains('\t'.code))
        assertTrue(normal.contains(0xfe0f))
        assertTrue(normal.contains(0x200e))
        assertFalse(normal.contains('\n'.code))
        assertFalse(normal.contains(0x00ad))
        assertFalse(strict.contains(' '.code))
        assertTrue(strict.contains(0x200e))
        assertEquals(2, normal.span(" \tvalue", UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(5, normal.spanBack("value \t", UnicodeSet.SpanCondition.CONTAINED))
    }
}
