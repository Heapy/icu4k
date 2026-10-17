package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.TrieIterator as Source
import com.ibm.icu.util.RangeValueIterator as SourceRanges
import io.github.heapy.icu4k.util.RangeValueIterator
import kotlin.test.*

class TrieIteratorOracleTest {
    private fun compare(source: Source,port: TrieIterator) {
        val s=SourceRanges.Element();val p=RangeValueIterator.Element()
        repeat(2) {
            var expectedStart=0;var count=0
            while(true) {
                val sn=source.next(s);val pn=port.next(p);assertEquals(sn,pn)
                assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))
                if(!sn)break
                assertEquals(expectedStart,p.start);assertTrue(p.limit>p.start)
                expectedStart=p.limit;assertTrue(++count<0x110001)
            }
            assertEquals(0x110000,expectedStart)
            assertFalse(source.next(s));assertFalse(port.next(p))
            assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))
            source.reset();port.reset()
        }
        repeat(7){assertEquals(source.next(s),port.next(p))}
        source.reset();port.reset()
        assertEquals(source.next(s),port.next(p));assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))
    }
    @Test fun allProfileRangesAndExtraction() {
        for(name in listOf("rfc3491","rfc3530cs","rfc3530csci","rfc3530mixp","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci"))for(mode in 0..3) {
            val sb=com.ibm.icu.impl.ICUBinary.getRequiredData("$name.spp");com.ibm.icu.impl.StringPrepDataReader(sb).readIndexes(16)
            val pb=ICUBinary.getRequiredData("$name.spp");StringPrepDataReader(pb).readIndexes(16)
            val st=com.ibm.icu.impl.CharTrie(sb,null);val pt=CharTrie(pb,null)
            fun extract(v: Int)=when(mode){0->v;1->v and 3;2->0;else->v ushr 8}
            compare(object:Source(st){override fun extract(value:Int)=extract(value)},object:TrieIterator(pt){override fun extract(value:Int)=extract(value)})
        }
    }
    @Test fun dummyCharIntRangesAndNull() {
        for(initial in listOf(0,17,-1,65536))for(lead in listOf(0,initial,19)) {
            compare(Source(com.ibm.icu.impl.CharTrie(initial,lead){0}),TrieIterator(CharTrie(initial,lead){0}))
            compare(Source(com.ibm.icu.impl.IntTrie(initial,lead){0}),TrieIterator(IntTrie(initial,lead){0}))
        }
        assertEquals("Argument trie cannot be null",assertFailsWith<IllegalArgumentException>{TrieIterator(null)}.message)
    }
}
