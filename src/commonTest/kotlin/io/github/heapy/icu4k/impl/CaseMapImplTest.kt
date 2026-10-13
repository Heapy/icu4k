package io.github.heapy.icu4k.impl

import kotlin.test.*

class CaseMapImplTest {
    @Test fun contextSeesBeyondMappingLimit() {
        val iter = CaseMapImpl.StringContextIterator("AΣB")
        iter.setLimit(2)
        assertEquals(65, iter.nextCaseMapCP())
        assertEquals(0x3a3, iter.nextCaseMapCP())
        iter.reset(1)
        assertEquals(66, iter.next())
        iter.reset(-1)
        assertEquals(65, iter.next())
        assertEquals(-1, iter.next())
        assertEquals(-1, iter.nextCaseMapCP())
    }
    @Test fun pairCanCrossMappingBoundary() {
        val text = charArrayOf(0xd800.toChar(), 0xdc00.toChar(), 0xdfff.toChar()).concatToString()
        val iter = CaseMapImpl.StringContextIterator(text)
        iter.setLimit(1)
        assertEquals(0x10000, iter.nextCaseMapCP())
        assertEquals(2, iter.getCPLength())
        iter.reset(1)
        assertEquals(0xdfff, iter.next())
        iter.moveToLimit()
        assertEquals(1, iter.getCPStart())
        iter.setLimit(-1)
        iter.moveTo(2)
        assertEquals(0xdfff, iter.nextCaseMapCP())
        iter.setCPStartAndLimit(0, 2)
        assertEquals(-1, iter.next())
    }
}
