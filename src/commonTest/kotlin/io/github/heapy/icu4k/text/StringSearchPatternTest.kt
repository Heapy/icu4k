// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class StringSearchPatternTest {
    @Test fun longTablesReuseAndInvalidation() {
        val c=RuleBasedCollator("")
        c.getCollationElementIterator("") // Initialize the optional expansion cache.
        val p=StringSearchPattern("æ".repeat(300)); val compiler=StringSearchPatternCompiler(p,c)
        compiler.initialize()
        assertTrue(p.CELength_>256); assertEquals(0,p.CE_!![p.CELength_])
        compiler.initializePatternPCETable()
        assertTrue(p.PCELength_>256); assertEquals(0L,p.PCE_!![p.PCELength_])
        val oldLength=p.PCELength_
        p.text_="é"
        compiler.initialize()
        assertNull(p.PCE_); assertEquals(oldLength,p.PCELength_)
        val original=p.CE_!!.copyOf()
        compiler.initializePatternCETable(); assertContentEquals(original,p.CE_)
        compiler.strength_=0; compiler.ceMask_=StringSearchPatternCompiler.getMask(0)
        compiler.initializePatternCETable(); assertEquals(1,p.CELength_)
    }
    @Test fun identicalCursorRestorationAndBoundaryFallbacks() {
        val p=StringSearchPattern("é"); val compiler=StringSearchPatternCompiler(p,RuleBasedCollator(""))
        compiler.strength_=15
        val text=StringCharacterIterator("!e\u0301?",2)
        assertTrue(compiler.checkIdentical(text,1,3)); assertEquals(2,text.getIndex())
        p.text_="e"; assertFalse(compiler.checkIdentical(text,1,3))
        compiler.strength_=2; assertTrue(compiler.checkIdentical(null,-1,-2))
        assertEquals(9,StringSearchPatternCompiler.nextBoundaryAfter(null,null,9))
        assertFalse(StringSearchPatternCompiler.isBreakBoundary(null,null,9))
        val chars=BreakIterator.getCharacterInstance(); chars.setText("e\u0301x")
        assertEquals(2,StringSearchPatternCompiler.nextBoundaryAfter(null,chars,0))
        assertFalse(StringSearchPatternCompiler.isBreakBoundary(chars,null,1))
        val supplementary=StringCharacterIterator("!a😀?",1,4,3)
        assertEquals(0x1f600,StringSearchPatternCompiler.codePointAt(supplementary,2))
        assertEquals(0x1f600,StringSearchPatternCompiler.codePointBefore(supplementary,4)); assertEquals(3,supplementary.getIndex())
        assertEquals("a😀\uffff\uffff",StringSearchPatternCompiler.getString(supplementary,1,5)); assertEquals(3,supplementary.getIndex())
    }
}
