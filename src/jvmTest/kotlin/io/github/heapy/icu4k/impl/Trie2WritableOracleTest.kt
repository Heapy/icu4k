// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Trie2Writable as Source
import com.ibm.icu.impl.Trie2 as SourceTrie
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.*

class Trie2WritableOracleTest {
    private fun field(a: Any,name: String,base: Boolean=false): Any? = (if(base) a.javaClass.superclass else a.javaClass).getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun state(a: Source,b: Trie2Writable,arrays: Boolean=true) {
        for(name in listOf("index2Length","dataCapacity","firstFreeBlock","isCompacted")) assertEquals(field(a,name),field(b,name),name)
        assertEquals(field(a,"index2NullOffset"),field(b,"writableIndex2NullOffset"))
        for(name in listOf("data16","indexLength","dataLength","initialValue","errorValue","highStart","highValueIndex","dataNullOffset","fHash","index2NullOffset")) assertEquals(field(a,name,true),field(b,name,true),name)
        if(arrays) for(name in listOf("index1","index2","data","map")) assertContentEquals(field(a,name) as IntArray,field(b,name) as IntArray,name)
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";else -> e.javaClass.simpleName+":"+e.message } }
    private fun bytes(a: SourceTrie): ByteArray = ByteArrayOutputStream().also { if(a is com.ibm.icu.impl.Trie2_16) a.serialize(it) else (a as com.ibm.icu.impl.Trie2_32).serialize(it) }.toByteArray()
    private fun freeze(a: Source,b: Trie2Writable,bits16: Boolean) {
        var frozenA: SourceTrie?=null;var frozenB: Trie2?=null
        assertEquals(outcome { frozenA=if(bits16) a.toTrie2_16() else a.toTrie2_32();Unit },outcome { frozenB=if(bits16) b.toTrie2_16() else b.toTrie2_32();Unit })
        state(a,b)
        if(frozenA!=null) {
            assertContentEquals(bytes(frozenA!!),frozenB!!.serialize())
            for(c in listOf(-1,0,65,0x7ff,0x800,0xd800,0xdbff,0xffff,0x10000,0x1f600,0x10ffff,0x110000)) assertEquals(frozenA!!.get(c),frozenB!![c])
        }
    }
    @Test fun randomWritesRangesFreeListsAndPostCompactionReuse() {
        val random=Random(78321)
        for(initial in listOf(0,3,-1,0x12345678)) {
            val a=Source(initial,-5);val b=Trie2Writable(initial,-5);state(a,b)
            repeat(1200) { step ->
                val cp=if(step%9==0) random.nextInt(-2,3) else random.nextInt(0x110000)
                val end=minOf(0x10ffff,cp+random.nextInt(0,6000));val value=if(step%4==0) initial else random.nextInt();val overwrite=random.nextBoolean()
                when(step%5) {
                    0 -> assertEquals(outcome { a.set(cp,value);Unit },outcome { b.set(cp,value);Unit })
                    1,2 -> assertEquals(outcome { a.setRange(cp,end,value,overwrite);Unit },outcome { b.setRange(cp,end,value,overwrite);Unit })
                    3 -> { val c=(cp and 0xffff).toChar();a.setForLeadSurrogateCodeUnit(c,value);b.setForLeadSurrogateCodeUnit(c,value) }
                    4 -> assertEquals(a.hashCode(),b.hashCode())
                }
                state(a,b,step%40==0)
                for(c in listOf(cp,end,random.nextInt(0x110000))) assertEquals(a.get(c),b[c])
            }
            freeze(a,b,true);freeze(a,b,false)
            repeat(40) { step ->
                val cp=random.nextInt(0x110000);val value=random.nextInt()
                assertEquals(outcome { a.setRange(cp,minOf(cp+64,0x10ffff),value,true);Unit },outcome { b.setRange(cp,minOf(cp+64,0x10ffff),value,true);Unit })
                state(a,b,step%5==0)
                for(c in listOf(cp,cp+1,0x10ffff)) assertEquals(outcome { a.get(c) },outcome { b[c] })
            }
            freeze(a,b,false)
        }
    }
    @Test fun copiedRealTriesAndExactSerializedCompaction() {
        val root=com.ibm.icu.impl.coll.CollationData::class.java.getDeclaredField("trie").apply { isAccessible=true }.get(com.ibm.icu.impl.coll.CollationRoot.getData()) as SourceTrie
        val target=io.github.heapy.icu4k.impl.coll.CollationRoot.getData().trie!!
        val a=Source(root);val b=Trie2Writable(target);state(a,b);freeze(a,b,false)
        for(c in 0..0x10ffff) assertEquals(a.get(c),b[c],"$c")
        val copyA=Source(a.toTrie2_32());val copyB=Trie2Writable(b.toTrie2_32());state(copyA,copyB);freeze(copyA,copyB,true)
    }
    @Test fun uniformHighStartAndSeparateSurrogateSlots() {
        for(mode in 0..4) {
            val a=Source(0,-1);val b=Trie2Writable(0,-1)
            when(mode) {
                1 -> { a.set(0xd800,7);b.set(0xd800,7) }
                2 -> { a.setForLeadSurrogateCodeUnit(0xd800.toChar(),9);b.setForLeadSurrogateCodeUnit(0xd800.toChar(),9) }
                3 -> { a.setRange(0,0x10ffff,7,true);b.setRange(0,0x10ffff,7,true) }
                4 -> { a.set(0x10ffff,7);b.set(0x10ffff,7) }
            }
            freeze(a,b,true);freeze(a,b,false)
            for(c in listOf(0,0xd800,0xdc00,0x10000,0x10ffff)) assertEquals(a.get(c),b[c])
            val aa=Source(a);val bb=Trie2Writable(b);state(aa,bb);freeze(aa,bb,false)
        }
    }
    @Test fun capacityGrowthAndRuntimeSizeFailures() {
        val a=Source(0,-1);val b=Trie2Writable(0,-1)
        for(block in 0 until 9000) { val cp=block*32;a.setRange(cp,cp+31,block+7,true);b.setRange(cp,cp+31,block+7,true) }
        state(a,b);freeze(a,b,true);freeze(a,b,false)
    }
}
