// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationFastLatinBuilderTest {
    @Test fun deterministicHeaderTableAndRuntimeIntegration() {
        val data=fastBuilderFixture(0,2)
        val builder=CollationFastLatinBuilder(); assertTrue(builder.forData(data))
        assertContentEquals(charArrayOf(0x205.toChar(),0.toChar(),0.toChar(),0.toChar(),0.toChar()),builder.getHeader())
        val table=builder.getTable(); assertEquals(451,table.size); assertEquals(0x400,table[0].code)
        assertNotSame(builder.getHeader(),builder.getHeader()); assertNotSame(table,builder.getTable())
        data.fastLatinTableHeader=builder.getHeader();data.fastLatinTable=table
        val primaries=CharArray(384);val settings=CollationSettings();val options=CollationFastLatin.getOptions(data,settings,primaries)
        assertEquals(-1,CollationFastLatin.compareUTF16(table,primaries,options,"\u0000","\u0001",0))
        assertEquals(0,CollationFastLatin.compareUTF16(table,primaries,options,"a","b",0))
        assertEquals("attempt to reuse a CollationFastLatinBuilder",assertFailsWith<IllegalStateException> { builder.forData(data) }.message)
    }
    @Test fun missingGroupsRetainPartialOutputAndForbidReuse() {
        val data=CollationData(null).apply { numScripts=26;scriptsIndex=CharArray(42);scriptStarts=charArrayOf(0.toChar(),0xff00.toChar()) }
        val builder=CollationFastLatinBuilder(); assertFalse(builder.forData(data))
        assertFailsWith<IndexOutOfBoundsException> { builder.getHeader() }; assertFails { builder.getTable() }
        assertFailsWith<IllegalStateException> { builder.forData(data) }
        val fresh=CollationFastLatinBuilder();assertEquals(0,fresh.getHeader().size);assertEquals(0,fresh.getTable().size)
    }
    @Test fun shortPrimaryRetryAndOverflow() {
        val retry=CollationFastLatinBuilder();assertTrue(retry.forData(fastBuilderFixture(50,20)))
        val overflow=CollationFastLatinBuilder();assertFalse(overflow.forData(fastBuilderFixture(0,60)))
        val longOverflow=CollationFastLatinBuilder();assertTrue(longOverflow.forData(fastBuilderFixture(129,20)))
    }
    @Test fun longVectorCapacityStorageAndFailureMutation() {
        val v=UVector64();assertTrue(v.isEmpty());assertEquals(32,v.getBuffer().size)
        v.setElementAt(Long.MIN_VALUE,31);assertEquals(Long.MIN_VALUE,v.elementAti(31));assertEquals(0,v.size())
        repeat(32) { v.addElement(it.toLong()) };val old=v.getBuffer()
        assertFailsWith<IndexOutOfBoundsException> { v.insertElementAt(7,-1) };assertNotSame(old,v.getBuffer());assertEquals(128,v.getBuffer().size);assertEquals(32,v.size())
        v.insertElementAt(Long.MAX_VALUE,1);assertEquals(Long.MAX_VALUE,v.elementAti(1));assertEquals(1,v.elementAti(2))
        while(v.size() < 131073) v.addElement(v.size().toLong())
        assertEquals(262144,v.getBuffer().size);val backing=v.getBuffer();v.removeAllElements();assertSame(backing,v.getBuffer());assertEquals(Long.MAX_VALUE,v.elementAti(1))
    }
}
internal fun fastBuilderFixture(digits: Int,latin: Int): CollationData = CollationData(null).apply {
    numScripts=26
    scriptsIndex=CharArray(42).also { a -> a[25]=6.toChar();for(i in 0..4) a[26+i]=(i+1).toChar() }
    scriptStarts=intArrayOf(0,0x300,0x400,0x500,0x600,0x1000,0x2000,0x3000,0xfe00,0xff00).map { it.toChar() }.toCharArray()
    val map=mutableMapOf<Int,Int>()
    for(c in 0 until CollationFastLatin.NUM_FAST_CHARS) {
        val cp=if(c < 384) c else c-384+0x2000
        val primary=if(c < digits) 0x1000+c else 0x2000+if(c < digits+latin) c-digits else 0
        map[cp]=(primary shl 16) or 0x0505
    }
    trie=iteratorFixtureTrie(map)
}
