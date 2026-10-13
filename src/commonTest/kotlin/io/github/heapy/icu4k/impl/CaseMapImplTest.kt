package io.github.heapy.icu4k.impl

import kotlin.test.*

class CaseMapImplTest {
    @Test fun stringCasingTracksChangesAndContext() {
        val edits = io.github.heapy.icu4k.text.Edits()
        val dest = StringBuilder("prefix")
        CaseMapImpl.toLower(1, 0, "AΣ AΣB", dest, edits)
        assertEquals("prefixaς aσb", dest.toString())
        assertEquals(0, edits.lengthDelta())
        assertEquals("i\u0307\u0301", CaseMapImpl.toLower(3, 0, "I\u0301"))
        assertEquals("i", CaseMapImpl.toLower(2, 0, "I\u0307"))
        assertEquals("strasse", CaseMapImpl.fold(0, "Straße"))
        assertEquals("sss", CaseMapImpl.fold(0x4000, "Straße"))
        val malformed = charArrayOf(0xdc00.toChar(), 'A', 0xd800.toChar()).concatToString()
        assertEquals(charArrayOf(0xdc00.toChar(), 'a', 0xd800.toChar()).concatToString(), CaseMapImpl.fold(0, malformed))
    }
    @Test fun uppercaseIncludesGreekContextRules() {
        assertEquals("STRASSE", CaseMapImpl.toUpper(1, 0, "Straße"))
        assertEquals("İI", CaseMapImpl.toUpper(2, 0, "iı"))
        assertEquals("ΑΪ ΑΫ", CaseMapImpl.toUpper(4, 0, "άι άυ"))
        assertEquals("ΑΙ\u0308 ΑΥ\u0308", CaseMapImpl.toUpper(4, 0, "α\u0301ι α\u0301υ"))
        assertEquals("Α\u0308Ι\u0308", CaseMapImpl.toUpper(4, 0, "α\u0301\u0308ι"))
        assertEquals("Ή ΗΑ", CaseMapImpl.toUpper(4, 0, "ή ήα"))
        val edits = io.github.heapy.icu4k.text.Edits()
        val output = StringBuilder()
        CaseMapImpl.toUpper(4, 0x4000, "Α άι", output, edits)
        assertEquals("ΑΪ", output.toString())
        assertEquals(0, edits.lengthDelta())
    }
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
