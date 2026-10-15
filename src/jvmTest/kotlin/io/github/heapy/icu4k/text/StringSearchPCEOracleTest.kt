// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.StringSearch as Source
import com.ibm.icu.text.RuleBasedCollator as SourceCollator
import com.ibm.icu.text.CollationElementIterator as SourceIterator
import com.ibm.icu.text.SearchIterator.ElementComparisonType as SourceType
import kotlin.random.Random
import kotlin.test.*

class StringSearchPCEOracleTest {
    private val pc=Source::class.java.declaredClasses.single { it.simpleName=="CollationPCE" }
    private val rc=pc.declaredClasses.single { it.simpleName=="Range" }
    private val bc=Source::class.java.declaredClasses.single { it.simpleName=="CEBuffer" }
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun set(a: Any,name: String,value: Any?) { a.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(a,value) }
    private fun processor(it: SourceIterator): Any = pc.getDeclaredConstructor(SourceIterator::class.java).apply { isAccessible=true }.newInstance(it)
    private fun call(a: Any,name: String,range: Any?): Any? = pc.getDeclaredMethod(name,rc).apply { isAccessible=true }.invoke(a,range)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun bufferState(a: Any): Any? {
        val buffer=field(a,"pceBuffer_")!!
        return listOf(field(buffer,"bufferIndex_"),(field(buffer,"buffer_") as Array<*>).map { x -> x?.let { listOf(field(it,"ce_"),field(it,"low_"),field(it,"high_")) } })
    }
    @Test fun streamsRangesReinitializationAndBackingBuffers() {
        val texts=listOf("","abc æ😀","é\u0327e\u0301","一𠀀", "a"+"\u0301".repeat(90)," - \u0301a",charArrayOf(0xd800.toChar(),'a',0xdc00.toChar()).concatToString())
        val init=pc.getDeclaredMethod("init",SourceIterator::class.java).apply { isAccessible=true }
        for(strength in listOf(0,1,2,3,15)) for(shift in listOf(false,true)) for(fcd in listOf(false,true)) {
            val a=SourceCollator(""); val b=RuleBasedCollator("")
            a.strength=strength; b.setStrength(strength); a.setAlternateHandlingShifted(shift); b.setAlternateHandlingShifted(shift)
            a.decomposition=if(fcd) 17 else 16; b.setDecomposition(if(fcd) 17 else 16)
            for(text in texts) {
                val ai=a.getCollationElementIterator(text); val bi=b.getCollationElementIterator(text)
                val ap=processor(ai); val bp=StringSearchPCE(bi)
                val ar=rc.getDeclaredConstructor().apply { isAccessible=true }.newInstance(); val br=StringSearchPCE.Range()
                for(backward in listOf(false,true)) {
                    ai.reset(); bi.reset(); init.invoke(ap,ai); bp.init(bi)
                    repeat(text.length*3+8) {
                        val name=if(backward) "previousProcessed" else "nextProcessed"
                        assertEquals(outcome { call(ap,name,ar) },outcome { if(backward) bp.previousProcessed(br) else bp.nextProcessed(br) },"$strength/$shift/$fcd/$text")
                        assertEquals(field(ar,"ixLow_"),br.ixLow_); assertEquals(field(ar,"ixHigh_"),br.ixHigh_)
                        assertEquals(field(ap,"isShifted_"),field(bp,"isShifted_")); assertEquals(bufferState(ap),bufferState(bp))
                    }
                }
                ai.reset(); bi.reset(); init.invoke(ap,ai); bp.init(bi)
                call(ap,"previousProcessed",null); bp.previousProcessed(null)
                // init intentionally retains buffered processed CEs, even with a new cursor.
                val anew=a.getCollationElementIterator("xyz"); val bnew=b.getCollationElementIterator("xyz")
                init.invoke(ap,anew); bp.init(bnew)
                assertEquals(outcome { call(ap,"previousProcessed",ar) },outcome { bp.previousProcessed(br) })
                assertEquals(bufferState(ap),bufferState(bp))
                assertEquals(outcome { init.invoke(ap,null as Any?) },outcome { bp.init(null) })
                assertEquals(outcome { call(ap,"nextProcessed",null) },outcome { bp.nextProcessed(null) })
                assertEquals(bufferState(ap),bufferState(bp))
            }
        }
    }
    @Test fun fullWidthProcessedWeightsAndWildcardComparison() {
        val a=processor(SourceCollator("").getCollationElementIterator("")); val b=StringSearchPCE(RuleBasedCollator("").getCollationElementIterator(""))
        val process=pc.getDeclaredMethod("processCE",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val compare=Source::class.java.getDeclaredMethod("compareCE64s",Long::class.javaPrimitiveType,Long::class.javaPrimitiveType,SourceType::class.java).apply { isAccessible=true }
        val random=Random(78381)
        for(strength in listOf(-2,0,1,2,3,15)) for(shift in listOf(false,true)) for(top in listOf(Int.MIN_VALUE,-1,0,1,0x10000000,Int.MAX_VALUE)) {
            for(x in listOf(a,b)) { set(x,"strength_",strength); set(x,"toShift_",shift); set(x,"variableTop_",top); set(x,"isShifted_",false) }
            for(ce in listOf(0,0xc0,-1,0x05000505,0x80000505.toInt())+List(100) { random.nextInt() }) {
                assertEquals(process.invoke(a,ce),b.processCE(ce)); assertEquals(field(a,"isShifted_"),field(b,"isShifted_"))
            }
        }
        val values=buildList { for(p in listOf(0,0x1234)) for(s in listOf(0,5,9)) for(t in listOf(0,5,9)) for(q in listOf(0,65535)) add((p.toLong() shl 48) or (s.toLong() shl 32) or (t.toLong() shl 16) or q.toLong()) }
        val types=SearchIterator.ElementComparisonType.entries.map { it as SearchIterator.ElementComparisonType? }+null
        for(type in types) {
            val source=type?.let { SourceType.valueOf(it.name) }
            for(x in values) for(y in values) assertEquals(compare.invoke(null,x,y,source),StringSearchPCE.compareCE64s(x,y,type))
            repeat(3000) { val x=random.nextLong(); val y=random.nextLong(); assertEquals(compare.invoke(null,x,y,source),StringSearchPCE.compareCE64s(x,y,type)) }
        }
    }
    private fun entry(a: Any?): Any? = a?.let { listOf(field(it,"ce_"),field(it,"lowIndex_"),field(it,"highIndex_")) }
    @Test fun circularBufferHistoryEvictionAliasesAndJamoSizing() {
        val might=bc.getDeclaredMethod("MIGHT_BE_JAMO_L",Char::class.javaPrimitiveType).apply { isAccessible=true }
        for(i in 0..65535) assertEquals(might.invoke(null,i.toChar()),StringSearchCEBuffer.MIGHT_BE_JAMO_L(i.toChar()))
        val get=bc.getDeclaredMethod("get",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val previous=bc.getDeclaredMethod("getPrevious",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val text="abc é😀ᄀ\u0301 ".repeat(20)
        for(type in SearchIterator.ElementComparisonType.entries.map { it as SearchIterator.ElementComparisonType? }+null) for(reverse in listOf(false,true)) {
            val ac=SourceCollator(""); val tc=RuleBasedCollator("")
            val ss=Source("abc",java.text.StringCharacterIterator(text),ac)
            ss.setElementComparisonType(type?.let { SourceType.valueOf(it.name) })
            val pat=field(ss,"pattern_")!!; set(pat,"PCELength_",3); set(pat,"text_","ᄀa😀ㄱ")
            ss.setIndex(if(reverse) text.length else 0)
            val ab=bc.getDeclaredConstructor(Source::class.java).apply { isAccessible=true }.newInstance(ss)
            val ti=tc.getCollationElementIterator(StringCharacterIterator(text)); ti.setOffset(if(reverse) text.length else 0)
            val tp=StringSearchPCE(ti)
            val tb=StringSearchCEBuffer(3,"ᄀa😀ㄱ",type,{ true },{ tp },bc.desiredAssertionStatus())
            assertEquals(field(ab,"bufSize_"),tb.bufSize_)
            var oldA: Any?=null; var oldT: StringSearchCEBuffer.CEI?=null
            for(i in 0..tb.bufSize_+5) {
                val am=if(reverse) previous else get
                val x=am.invoke(ab,i); val y=if(reverse) tb.getPrevious(i) else tb.get(i)
                assertEquals(entry(x),entry(y)); assertEquals(field(ab,"firstIx_"),tb.firstIx_); assertEquals(field(ab,"limitIx_"),tb.limitIx_)
                if(i==0) { oldA=x; oldT=y }
                if(i==tb.bufSize_) { assertSame(oldA,x); assertSame(oldT,y) }
                assertSame(x,get.invoke(ab,i)); assertSame(y,tb.get(i))
            }
            assertEquals(outcome { get.invoke(ab,0) },outcome { tb.get(0) })
            assertEquals(outcome { get.invoke(ab,tb.limitIx_+1) },outcome { tb.get(tb.limitIx_+1) })
        }
    }
}
