// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationKeys as Source
import com.ibm.icu.impl.coll.CollationSettings as SourceSettings
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.impl.coll.CollationIterator as SourceIterator
import com.ibm.icu.impl.coll.FCDUTF16CollationIterator as SourceFCD
import com.ibm.icu.impl.coll.KeyOracleCallback
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CollationKeysOracleTest {
    private class SourceSink(capacity: Int,val grow: Boolean): Source.SortKeyByteSink(ByteArray(capacity)) {
        val calls=mutableListOf<String>()
        fun buffer(): ByteArray = buffer_
        override fun Resize(appendCapacity: Int,length: Int): Boolean {
            calls.add("resize/$appendCapacity/$length");if(!grow) return false
            buffer_=buffer_.copyOf(maxOf(length+appendCapacity,buffer_.size*2+1));return true
        }
        override fun AppendBeyondCapacity(bytes: ByteArray,start: Int,n: Int,length: Int) {
            calls.add("beyond/$start/$n/$length");if(Resize(n,length)) bytes.copyInto(buffer_,length,start,start+n)
        }
    }
    private fun sourceSettings(s: CollationSettings): SourceSettings = SourceSettings::class.java.getDeclaredConstructor().apply { isAccessible=true }.newInstance().also { source ->
        for(name in listOf("options","variableTop","reorderTable","reorderCodes","reorderRanges","minHighNoReorder")) SourceSettings::class.java.getDeclaredField(name).apply { isAccessible=true }.set(source,CollationSettings::class.java.getDeclaredField(name).apply { isAccessible=true }.get(s))
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t=if(e is InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun compare(sd: SourceData,td: CollationData,text: String,settings: CollationSettings,minLevel: Int=1,stop: Int=-1,capacity: Int=4,grow: Boolean=true,preflight: Boolean=true) {
        val s=SourceFCD(sd,settings.isNumeric(),text,0);val t=FCDUTF16CollationIterator(td,settings.isNumeric(),text,0)
        val a=SourceSink(capacity,grow);val b=KeysTestSink(capacity,grow);val callbackA=KeyOracleCallback(stop);val callsB=mutableListOf<Int>()
        val callbackB=object: CollationKeys.LevelCallback() { override fun needToWrite(level: Int): Boolean { callsB.add(level);return level!=stop } }
        assertEquals(outcome { Source.writeSortKeyUpToQuaternary(s,sd.compressibleBytes,sourceSettings(settings),a,minLevel,callbackA,preflight) },outcome { CollationKeys.writeSortKeyUpToQuaternary(t,td.compressibleBytes,settings,b,minLevel,callbackB,preflight) },"$text/${settings.options}/$minLevel/$stop/$capacity/$grow/$preflight")
        assertEquals(a.NumberOfBytesAppended(),b.NumberOfBytesAppended());assertEquals(a.Overflowed(),b.Overflowed());assertEquals(a.GetRemainingCapacity(),b.GetRemainingCapacity())
        assertContentEquals(a.buffer(),b.buffer());assertEquals(a.calls,b.calls);assertEquals(callbackA.calls,callsB)
        assertEquals(s.offset,t.getOffset());assertEquals(s.getCEsLength(),t.getCEsLength());assertContentEquals(s.getCEs(),t.getCEs())
        assertEquals(SourceIterator::class.java.getDeclaredField("cesIndex").apply { isAccessible=true }.get(s),CollationIterator::class.java.getDeclaredField("cesIndex").apply { isAccessible=true }.get(t))
    }
    @Test fun realRootAndTailoringsAllLevelsAndOptions() {
        val field=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").apply { isAccessible=true }
        val sources=listOf(com.ibm.icu.impl.coll.CollationRoot.getData())+listOf("fr","es@collation=traditional","de@collation=phonebook","th").map { field.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }
        val random=Random(78315);val pieces=listOf("a","A","ch","ll","é","e\u0301","a\u0315\u0300","\u0f73","😀","\ud800","\udc00","角","각","12","١","𝟘","-"," ","\ufffe")
        val texts=listOf("","a","a\ufffeb","côte","coté","a".repeat(230),"A".repeat(120)," ".repeat(130))+List(30) { buildString { repeat(random.nextInt(1,14)) { append(pieces.random(random)) } } }
        for(sd in sources) {
            val td=CollationDataOracleTest().importSource(sd)
            for(strength in listOf(0,1,2,3,15)) for(case in listOf(0,0x200,0x300,0x400,0x700)) for(mode in 0..7) {
                val settings=CollationSettings().apply {
                    options=(strength shl 12) or case or (if(mode and 1!=0) 4 else 0) or (if(mode and 2!=0) 0x800 else 0) or (if(mode and 4!=0) 2 else 0)
                    variableTop=td.getLastPrimaryForGroup(4097)
                    if(mode and 3==3) setReordering(td,intArrayOf(14,25))
                }
                for(text in texts) compare(sd,td,text,settings)
            }
        }
    }
    @Test fun callbacksMinLevelsAndFixedCapacityPreflight() {
        val sd=com.ibm.icu.impl.coll.CollationRoot.getData();val td=CollationRoot.getData()
        val s=CollationSettings().apply { options=0x3704;variableTop=td.getLastPrimaryForGroup(4097) }
        for(text in listOf("","aAbcé\ufffez","a".repeat(160),"   a - ")) for(level in listOf(-1,0,1,2,3,4,5,6,31,32)) for(stop in -1..5) compare(sd,td,text,s,level,stop)
        for(capacity in 0..12) for(preflight in listOf(false,true)) for(text in listOf("","abcdef","a\ufffeb"," ".repeat(20)+"A")) compare(sd,td,text,s,capacity=capacity,grow=false,preflight=preflight)
    }
    @Test fun syntheticRareWeightsAndCompressionTransitions() {
        val random=Random(78317)
        val weights=listOf(0L,compareCE(0,0x600),compareCE(0,0,0x8500),compareCE(0x10000000),compareCE(0x30000000),compareCE(0x30000000,0x400),compareCE(0x30000000,0x600),compareCE(0x30000000,0x500,0x8500),compareCE(0x30000000,0x500,0x4500),compareCE(0x30000000,0x500,0x540),compareCE(0x30000000,0x500,0x580),Collation.ceFromSimpleCE32(Collation.MERGE_SEPARATOR_CE32))
        val fixtures=weights.map { longArrayOf(it) }+List(80) { LongArray(random.nextInt(1,25)) { weights.random(random) } }
        for(values in fixtures) {
            val td=compareFixtureData(values).apply { compressibleBytes=BooleanArray(256) { it%2==0 } }
            val sd=CollationDataOracleTest().exportTarget(td)
            for(strength in listOf(0,1,2,3,15)) for(flags in listOf(0,4,0x800,0x804,0x200,0x300,0x400,0x500,0x700)) {
                val settings=CollationSettings().apply { options=(strength shl 12) or flags;variableTop=0x20000000 }
                compare(sd,td,"x",settings)
            }
        }
    }
    @Test fun privateLevelBuffersAndWeightEncoders() {
        val sourceClass=Class.forName("com.ibm.icu.impl.coll.CollationKeys\$SortKeyLevel")
        val targetClass=Class.forName("io.github.heapy.icu4k.impl.coll.CollationKeys\$SortKeyLevel")
        var a=sourceClass.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
        var b=targetClass.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
        val random=Random(78318)
        val methods=listOf("appendByte","appendWeight16","appendWeight32","appendReverseWeight16","getAt","ensureCapacity")
        for(i in 0 until 3000) {
            if(i%60==0) {
                a=sourceClass.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
                b=targetClass.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
            }
            val name=methods[i%methods.size]
            val type=if(name=="appendWeight32") Long::class.javaPrimitiveType else Int::class.javaPrimitiveType
            val value: Any=if(name=="appendWeight32") random.nextLong() else if(name=="getAt") random.nextInt(-2,3000) else random.nextInt()
            val sm=sourceClass.getDeclaredMethod(name,type).apply { isAccessible=true }
            val tm=targetClass.getDeclaredMethod(name,type).apply { isAccessible=true }
            // Reset each series as well: ensureCapacity doubles even when no growth is needed.
            val argument=if(name=="ensureCapacity") random.nextInt(-10,50) else value
            assertEquals(outcome { sm.invoke(a,argument) },outcome { tm.invoke(b,argument) },name)
            for(field in listOf("len","buffer")) {
                val expected=sourceClass.getDeclaredField(field).apply { isAccessible=true }.get(a)
                val actual=targetClass.getDeclaredField(field).apply { isAccessible=true }.get(b)
                if(expected is ByteArray) assertContentEquals(expected,actual as ByteArray) else assertEquals(expected,actual)
            }
        }
    }
    @Test fun sinkFailuresAndCounterMutation() {
        val random=Random(78316)
        for(grow in listOf(false,true)) {
            val a=SourceSink(4,grow);val b=KeysTestSink(4,grow)
            repeat(500) { step ->
                val bytes=ByteArray(random.nextInt(0,12)) { random.nextInt().toByte() };val count=random.nextInt(-1,15)
                when(step%3) {
                    0 -> { val app=random.nextInt(-2,15);val capacity=random.nextInt(0,12);a.setBufferAndAppended(ByteArray(capacity),app);b.setBufferAndAppended(ByteArray(capacity),app) }
                    1 -> assertEquals(outcome { a.Append(bytes,count) },outcome { b.Append(bytes,count) })
                    2 -> { val value=random.nextInt();assertEquals(outcome { a.Append(value) },outcome { b.Append(value) }) }
                }
                assertEquals(a.NumberOfBytesAppended(),b.NumberOfBytesAppended());assertContentEquals(a.buffer(),b.buffer());assertEquals(a.calls,b.calls)
            }
        }
    }
}
