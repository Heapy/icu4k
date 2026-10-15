// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.BOCSU as Source
import com.ibm.icu.util.ByteArrayWrapper as SourceSink
import io.github.heapy.icu4k.util.ByteArrayWrapper
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BOCSUOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val error=if(e is InvocationTargetException) e.targetException else e
        when(error) { is IndexOutOfBoundsException -> "bounds";is NullPointerException -> "null";else -> error.javaClass.simpleName+":"+error.message }
    }
    @Test fun diffBoundariesRandomValuesAndPartialBufferWrites() {
        val source=Source::class.java.getDeclaredMethod("writeDiff",Int::class.javaPrimitiveType,ByteArray::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val target=BOCSU::class.java.getDeclaredMethod("writeDiff",Int::class.javaPrimitiveType,ByteArray::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val random=Random(78310)
        val thresholds=listOf(-1114111,-192786,-10708,-81,-80,0,80,81,10707,192785,1114111,Int.MIN_VALUE,Int.MAX_VALUE)
        val values=thresholds.flatMap { t -> (-3..3).map { t+it } }+List(20000) { random.nextInt() }
        for((i,diff) in values.withIndex()) {
            val size=if(i%5==0) i%4 else 9;val offset=if(i%7==0) -1 else i%6
            val a=ByteArray(size) { 42 };val b=a.copyOf()
            assertEquals(outcome { source.invoke(null,diff,a,offset) },outcome { target.invoke(BOCSU,diff,b,offset) },"diff=$diff/$offset/$size")
            assertContentEquals(a,b,"partial diff=$diff")
        }
    }
    @Test fun customSequencesAndVirtualCapacityCallbacks() {
        class Sequence(val text: String,val calls: MutableList<String>): CharSequence {
            override val length: Int get() { calls.add("length");return text.length }
            override fun get(index: Int): Char { calls.add("get/$index");return text[index] }
            override fun subSequence(startIndex: Int,endIndex: Int): CharSequence = error("unused")
        }
        val texts=listOf("abc",charArrayOf(0xd800.toChar(),0xdc00.toChar()).concatToString(),charArrayOf(0xd800.toChar(),'a').concatToString())
        for(text in texts) for(end in 1..text.length+1) {
            val callsA=mutableListOf<String>();val callsB=mutableListOf<String>()
            val a=object: SourceSink(ByteArray(0),0) {
                override fun ensureCapacity(capacity: Int): SourceSink { callsA.add("capacity/$capacity");return super.ensureCapacity(capacity) }
            }
            val b=object: ByteArrayWrapper(ByteArray(0),0) {
                override fun ensureCapacity(capacity: Int): ByteArrayWrapper { callsB.add("capacity/$capacity");return super.ensureCapacity(capacity) }
            }
            assertEquals(outcome { Source.writeIdenticalLevelRun(0,Sequence(text,callsA),0,end,a) },outcome { BOCSU.writeIdenticalLevelRun(0,Sequence(text,callsB),0,end,b) })
            assertEquals(callsA,callsB);assertEquals(a.size,b.size);assertContentEquals(a.bytes,b.bytes)
        }
    }
    @Test fun runsSurrogateBoundariesGrowthAndFailureState() {
        val random=Random(78311)
        val texts=listOf("","a","abc","中文","\ufffe","a\ufffeb",charArrayOf(0xd800.toChar(),0xdc00.toChar(),0xd800.toChar(),'x',0xdc00.toChar()).concatToString())+
            List(300) { CharArray(random.nextInt(1,130)) { random.nextInt(65536).toChar() }.concatToString() }
        for((i,text) in texts.withIndex()) for(capacity in listOf(0,3,16,31,257)) {
            val a=SourceSink(ByteArray(capacity) { 19 },minOf(capacity,2));val b=ByteArrayWrapper(a.bytes.copyOf(),a.size)
            val prev=if(i%3==0) random.nextInt() else 0
            val start=if(i%5==0) -1 else if(text.isEmpty()) 0 else i%text.length
            val end=if(i%7==0) text.length+1 else text.length
            assertEquals(outcome { Source.writeIdenticalLevelRun(prev,text,start,end,a) },outcome { BOCSU.writeIdenticalLevelRun(prev,text,start,end,b) },"run/$i/$capacity")
            assertEquals(a.size,b.size);assertContentEquals(a.bytes,b.bytes,"buffer/$i/$capacity")
        }
    }
}
