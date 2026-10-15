// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UCharacterIterator
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.text.UCharacterIterator as SourceCursor
import com.ibm.icu.impl.coll.IterCollationIterator as SourceIter
import com.ibm.icu.impl.coll.FCDIterCollationIterator as SourceFCD
import com.ibm.icu.impl.coll.UVector32 as SourceOffsets

class IterCollationIteratorOracleTest {
    private val sd = com.ibm.icu.impl.coll.CollationRoot.getData()
    private val td = CollationDataOracleTest().importSource(sd)
    @Test fun CEStreamsReverseOffsetsAndCallerCursorWithAllSegmentStates() {
        for (fcd in listOf(false,true)) for (numeric in listOf(false,true)) for (text in texts()) {
            val a = SourceCursor.getInstance(text); val b = UCharacterIterator.getInstance(text)
            val s = if (fcd) SourceFCD(sd,numeric,a,0) else SourceIter(sd,numeric,a)
            val t = if (fcd) FCDIterCollationIterator(td,numeric,b,0) else IterCollationIterator(td,numeric,b)
            var count = 0
            while (true) { val ce = s.nextCE(); assertEquals(ce,t.nextCE(),"forward/$fcd/$numeric/$text"); state(s,t,a,b); if (ce == Collation.NO_CE) break; assertTrue(++count < 5000) }
            val so = SourceOffsets(); val to = UVector32()
            while (true) { val ce = s.previousCE(so); assertEquals(ce,t.previousCE(to),"back/$fcd/$numeric/$text"); state(s,t,a,b); assertEquals(so.size(),to.size()); assertContentEquals(so.buffer,to.getBuffer()); if (ce == Collation.NO_CE) break; assertTrue(++count < 10000) }
        }
    }
    @Test fun directionChangesResetAndIndependentStartIndex() {
        val random = Random(7839)
        for (text in texts()) for (initial in listOf(0,text.length)) {
            val a = SourceCursor.getInstance(text); val b = UCharacterIterator.getInstance(text)
            a.setIndex(initial); b.setIndex(initial)
            val s = SourceFCD(sd,false,a,initial); val t = FCDIterCollationIterator(td,false,b,initial)
            repeat(180) {
                when (random.nextInt(10)) {
                    in 0..4 -> assertEquals(outcome { s.nextCodePoint() },outcome { t.nextCodePoint() },"next/$text")
                    in 5..8 -> assertEquals(outcome { s.previousCodePoint() },outcome { t.previousCodePoint() },"prev/$text")
                    else -> { val p = random.nextInt(text.length + 1); s.resetToOffset(p); t.resetToOffset(p) }
                }
                state(s,t,a,b)
            }
        }
        val a = SourceCursor.getInstance("abc"); val b = UCharacterIterator.getInstance("abc")
        val s = SourceFCD(sd,false,a,17); val t = FCDIterCollationIterator(td,false,b,17)
        repeat(5) { assertEquals(outcome { s.previousCodePoint() },outcome { t.previousCodePoint() }); state(s,t,a,b) }
    }
    @Test fun nullIteratorsFailureOrderAndInheritedEquality() {
        val s = SourceIter(sd,false,null); val t = IterCollationIterator(td,false,null)
        assertEquals(outcome { s.offset },outcome { t.getOffset() })
        assertEquals(outcome { s.nextCE() },outcome { t.nextCE() }); assertEquals(s.getCEsLength(),t.getCEsLength())
        assertEquals(outcome { s.resetToOffset(0) },outcome { t.resetToOffset(0) }); assertEquals(s.getCEsLength(),t.getCEsLength())
        val a = SourceCursor.getInstance("abc"); val b = UCharacterIterator.getInstance("abc")
        val fs = SourceFCD(sd,false,a,0); val ft = FCDIterCollationIterator(td,false,b,0)
        for (p in listOf(-1,4,Int.MAX_VALUE)) { assertEquals(outcome { fs.resetToOffset(p) },outcome { ft.resetToOffset(p) }); state(fs,ft,a,b) }
        val fs2 = SourceFCD(sd,false,SourceCursor.getInstance("xyz"),0); val ft2 = FCDIterCollationIterator(td,false,UCharacterIterator.getInstance("xyz"),0)
        assertEquals(fs.equals(fs2),ft.equals(ft2))
        fs.nextCodePoint(); ft.nextCodePoint(); assertEquals(fs.equals(fs2),ft.equals(ft2))
    }
    private fun texts(): List<String> {
        val random = Random(7811)
        val pieces = listOf("a","ch","é","\u0315\u0300","\u0345\u0323\u0301","\u0f73","\u0f75","\u0f81","😀","\ud800","\udc00","𝅗𝅥𝅥\u0300","𝟘𝟙𝟚","각","١2","\u0000")
        return listOf("","abc","a\u0315\u0300b","é\u0323","\ud800\u0315\u0300\udc00","\u0f73\u0f75\u0f81","0123456789".repeat(60)) + List(40) { buildString { repeat(random.nextInt(1,25)) { append(pieces.random(random)) } } }
    }
    private fun state(s: SourceIter,t: IterCollationIterator,a: SourceCursor,b: UCharacterIterator) {
        assertEquals(a.index,b.getIndex()); assertEquals(s.offset,t.getOffset()); assertEquals(s.getCEsLength(),t.getCEsLength()); assertContentEquals(s.getCEs(),t.getCEs())
        if (s is SourceFCD && t is FCDIterCollationIterator) for (name in listOf("state","start","pos","limit","s","normalized")) {
            val source = SourceFCD::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(s)
            val target = FCDIterCollationIterator::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(t)
            assertEquals(source?.toString(),target?.toString(),name)
        }
    }
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
}
