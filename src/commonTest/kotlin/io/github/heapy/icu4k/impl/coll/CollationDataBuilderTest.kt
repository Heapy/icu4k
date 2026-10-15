// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class CollationDataBuilderTest {
    private fun ces(b: CollationDataBuilder,prefix: String,s: String): List<Long> {
        val out=LongArray(31);val n=b.getCEs(prefix,s,out,0);return out.take(n)
    }
    @Test fun contextualMappingsAreReadableBeforeAndAfterBuild() {
        val b=CollationDataBuilder();val root=CollationRoot.getData();b.initForTailoring(root)
        val a=compareCE(0x30000000);val c=compareCE(0x40000000);val d=compareCE(0x50000000)
        b.add("","a",longArrayOf(a),1);b.add("","ab",longArrayOf(c,d),2);b.add("x","a",longArrayOf(d),1)
        assertEquals(listOf(a),ces(b,"","a"));assertEquals(listOf(c,d),ces(b,"","ab"));assertEquals(listOf(d),ces(b,"x","a"))
        b.add("x","ab",longArrayOf(a,c),2);assertEquals(listOf(a,c),ces(b,"x","ab"))
        val data=CollationData(root.nfcImpl);b.enableFastLatin();b.build(data)
        assertEquals(listOf(c,d),iteratorForward(UTF16CollationIterator(data,false,"ab",0)))
        assertEquals(listOf(a,c),iteratorForward(UTF16CollationIterator(data,false,"xab",1)))
        assertSame(root,data.base);assertSame(b.ce32s.getBuffer(),data.ce32s);assertSame(b.ce64s.getBuffer(),data.ces);assertTrue(data.unsafeBackwardSet!!.isFrozen())
        assertFailsWith<IllegalStateException> { b.add("","z",longArrayOf(a),1) }
        assertFailsWith<IllegalStateException> { b.build(CollationData(root.nfcImpl)) }
    }
    @Test fun allCeEncodingFormsAndOutputLimit() {
        val b=CollationDataBuilder();b.initForTailoring(CollationRoot.getData())
        assertEquals(0x30000505,b.encodeCEs(longArrayOf(compareCE(0x30000000)),1))
        assertTrue(Collation.hasCE32Tag(b.encodeCEs(longArrayOf(compareCE(0x30040200)),1),1))
        assertTrue(Collation.hasCE32Tag(b.encodeCEs(longArrayOf(compareCE(0,0x607)),1),2))
        assertTrue(Collation.hasCE32Tag(b.encodeCEs(longArrayOf(compareCE(0x30000000),compareCE(0,0x600)),2),4))
        assertTrue(Collation.hasCE32Tag(b.encodeCEs(longArrayOf(compareCE(0x30000000),compareCE(0x40000000)),2),5))
        val expanded=(0x30040203L shl 32) or 0x06070506L
        assertTrue(Collation.hasCE32Tag(b.encodeCEs(longArrayOf(expanded),1),6))
        b.add("","a",longArrayOf(compareCE(0x30000000)),1)
        val out=LongArray(31) { 99 };assertEquals(42,b.getCEs("a".repeat(40),out,2));assertEquals(99,out[0]);assertEquals(compareCE(0x30000000),out[30])
        val fresh=CollationTailoring(null)
        CollationDataReader.read(null,io.github.heapy.icu4k.impl.ICUBinary.getRequiredData("coll/ucadata.icu"),fresh)
        val base=fresh.data!!;val fast=CollationFastLatinBuilder();assertTrue(fast.forData(base));base.fastLatinTableHeader=fast.getHeader();base.fastLatinTable=fast.getTable()
        val data=CollationData(b.nfcImpl);val empty=CollationDataBuilder();empty.initForTailoring(base);empty.optimize(UnicodeSet().add(0));empty.enableFastLatin();empty.build(data)
        assertSame(base.fastLatinTable,data.fastLatinTable)
    }
    @Test fun copyingModifiersSuppressionAndInitialFailures() {
        val root=CollationRoot.getData();val b=CollationDataBuilder();b.initForTailoring(root)
        assertFalse(b.hasMappings());assertEquals(0,b.encodeCEs(null,0))
        val original=compareCE(0x30000000);b.add("","ab",longArrayOf(original),1)
        val copy=CollationDataBuilder();copy.initForTailoring(root)
        copy.copyFrom(b,object: CollationDataBuilder.CEModifier {
            override fun modifyCE32(ce32: Int): Long = compareCE(0x40000000)
            override fun modifyCE(ce: Long): Long = Collation.NO_CE
        })
        assertEquals(listOf(compareCE(0x40000000)),ces(copy,"","ab"))
        copy.suppressContractions(UnicodeSet().add('a'.code));assertFalse(copy.contextChars.contains('a'.code))
        assertFailsWith<IllegalStateException> { b.initForTailoring(root) }
        assertFailsWith<IllegalArgumentException> { b.encodeCEs(null,32) }
        assertFailsWith<IllegalArgumentException> { CollationDataBuilder().initForTailoring(null) }
    }
}
