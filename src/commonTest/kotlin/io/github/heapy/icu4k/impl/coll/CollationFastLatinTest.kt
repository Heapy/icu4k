// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationFastLatinTest {
    private fun compare(a: String,b: String,strength: Int=2,flags: Int=0): Int {
        val d=fastLatinFixture(); val s=CollationSettings().apply { options=(strength shl 12) or flags or 0x10 }
        val p=CharArray(384); val options=CollationFastLatin.getOptions(d,s,p)
        return CollationFastLatin.compareUTF16(d.fastLatinTable,p,options,a,b,0)
    }
    @Test fun primaryExpansionContractionAndUnsupportedFallback() {
        assertEquals(-1,compare("a","b")); assertEquals(0,compare("e","ab")); assertEquals(-1,compare("c","ch"))
        assertEquals(-2,compare("c☃","a")); assertEquals(-2,compare("☃","a"))
        assertEquals(0,compare("\u0000a","a")); assertEquals(-1,compare("\ufffe","a")); assertEquals(1,compare("\uffff","b"))
    }
    @Test fun secondaryCaseTertiaryAndQuaternary() {
        assertEquals(0,compare("a","á",0)); assertEquals(-1,compare("a","á",1))
        assertEquals(-2,compare("a","á",1,CollationSettings.BACKWARD_SECONDARY))
        assertEquals(0,compare("a","A")); assertEquals(-1,compare("a","A",2,CollationSettings.CASE_FIRST))
        assertEquals(1,compare("a","A",2,CollationSettings.CASE_FIRST_AND_UPPER_MASK))
        assertEquals(-1,compare("a","A",0,CollationSettings.CASE_LEVEL))
        assertEquals(1,compare("a","A",0,CollationSettings.CASE_LEVEL or CollationSettings.UPPER_FIRST))
        assertEquals(0,compare("a ","a",2,CollationSettings.SHIFTED)); assertEquals(1,compare("a ","a",3,CollationSettings.SHIFTED))
        assertEquals(-2,compare("1","2",2,CollationSettings.NUMERIC))
    }
    @Test fun optionsPrimaryCacheAndCharacterIndexes() {
        val d=fastLatinFixture(); val p=CharArray(384); val s=CollationSettings()
        assertEquals(0x0bff2010,CollationFastLatin.getOptions(d,s,p)); assertEquals(0x1000,p['a'.code].code); assertEquals(0xc00,p[' '.code].code)
        s.setAlternateHandlingShifted(true); assertEquals(0xc082014,CollationFastLatin.getOptions(d,s,p)); assertEquals(0,p[' '.code].code)
        s.setFlag(CollationSettings.NUMERIC,true); CollationFastLatin.getOptions(d,s,p); for(c in '0'..'9') assertEquals(0,p[c.code].code)
        assertEquals(383,CollationFastLatin.getCharIndex('\u017f')); assertEquals(384,CollationFastLatin.getCharIndex('\u2000')); assertEquals(447,CollationFastLatin.getCharIndex('\u203f')); assertEquals(-1,CollationFastLatin.getCharIndex('\u2040'))
        assertEquals(-1,CollationFastLatin.getOptions(CollationData(null),null,null))
    }
}
internal fun fastLatinFixture(): CollationData = CollationData(null).apply {
    fastLatinTableHeader=charArrayOf(0x0205.toChar(),0xc00.toChar(),0xc08.toChar(),0xc10.toChar(),0xc18.toChar())
    fastLatinTable=CharArray(456) { 1.toChar() }.also { t ->
        t[0]=0.toChar(); t['a'.code]=0x10a8.toChar(); t['A'.code]=0x10b0.toChar(); t['á'.code]=0x10c8.toChar(); t['b'.code]=0x14a8.toChar()
        t[' '.code]=0xc00.toChar(); t['-'.code]=0xc08.toChar(); t[384]=0xc10.toChar()
        for(c in '0'..'9') t[c.code]=(0x2000+(c-'0')*0x400+0xa8).toChar()
        t['c'.code]=0x400.toChar(); t[448]=0x400.toChar(); t[449]=0x18a8.toChar(); t[450]=(0x400+'h'.code).toChar(); t[451]=0x1ca8.toChar(); t[452]=0x1ff.toChar()
        t['e'.code]=0x805.toChar(); t[453]=0x10a8.toChar(); t[454]=0x14a8.toChar()
    }
}
