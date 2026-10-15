// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.ContractionsAndExpansions as Source
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.text.UnicodeSet as SourceSet
import io.github.heapy.icu4k.text.UnicodeSet
import java.util.IdentityHashMap
import kotlin.test.*

class ContractionsAndExpansionsOracleTest {
    private class Events(val stop: Int=-1) {
        val arrays=IdentityHashMap<LongArray,Int>();val events=mutableListOf<Any>()
        fun ce(value: Long) { events.add(value);if(events.size==stop) throw IllegalStateException("stop") }
        fun expansion(a: LongArray?,start: Int,length: Int) {
            val id=if(a==null) -1 else arrays.getOrPut(a) { arrays.size }
            val values=if(a!=null && start>=0 && length>=0 && start<=a.size-length) a.slice(start until start+length) else null
            events.add(listOf(id,a?.size,start,length,values))
            if(events.size==stop) throw IllegalStateException("stop")
        }
    }
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun outcome(block: () -> Unit): Any? = try { block();null } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun compare(sd: SourceData,td: CollationData,prefixes: Boolean,codePoints: List<Int>?,withSink: Boolean=true,stop: Int=-1,reuse: Boolean=false,withSets: Boolean=true) {
        val sc=if(withSets) SourceSet() else null;val se=if(withSets) SourceSet() else null;val tc=if(withSets) UnicodeSet() else null;val te=if(withSets) UnicodeSet() else null
        val a=Events(stop);val b=Events(stop)
        val source=Source(sc,se,if(!withSink) null else object: Source.CESink {
            override fun handleCE(ce: Long) = a.ce(ce)
            override fun handleExpansion(ces: LongArray?,start: Int,length: Int) = a.expansion(ces,start,length)
        },prefixes)
        val target=ContractionsAndExpansions(tc,te,if(!withSink) null else object: ContractionsAndExpansions.CESink {
            override fun handleCE(ce: Long) = b.ce(ce)
            override fun handleExpansion(ces: LongArray?,start: Int,length: Int) = b.expansion(ces,start,length)
        },prefixes)
        fun state() {
            assertEquals(sc?.toPattern(false),tc?.toPattern(false));assertEquals(se?.toPattern(false),te?.toPattern(false));assertEquals(a.events,b.events)
            for(name in listOf("checkTailored","suffix")) assertEquals(field(source,name),field(target,name),name)
            assertEquals(field(source,"unreversedPrefix").toString(),field(target,"unreversedPrefix").toString())
            assertEquals((field(source,"tailored") as SourceSet).toPattern(false),(field(target,"tailored") as UnicodeSet).toPattern(false))
            assertEquals((field(source,"tailored") as SourceSet).isFrozen,(field(target,"tailored") as UnicodeSet).isFrozen())
            assertContentEquals(field(source,"ces") as LongArray,field(target,"ces") as LongArray)
            val sr=field(source,"ranges") as SourceSet?;val tr=field(target,"ranges") as UnicodeSet?;assertEquals(sr?.toPattern(false),tr?.toPattern(false))
        }
        repeat(if(reuse) 2 else 1) {
            if(codePoints==null) {
                assertEquals(outcome { source.forData(sd) },outcome { target.forData(td) });state()
            } else for(c in codePoints) {
                assertEquals(outcome { source.forCodePoint(sd,c) },outcome { target.forCodePoint(td,c) },"cp=$c");state()
            }
        }
    }
    @Test fun fullRootAndTailoringEnumerationWithCallbacks() {
        val field=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").apply { isAccessible=true }
        val sources=listOf(com.ibm.icu.impl.coll.CollationRoot.getData())+listOf("fr","es@collation=traditional","de@collation=phonebook","th","zh").map { field.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }+
            listOf("&a < x|b < xy|b < x|bc / d", "&a < 😀|b < 😀|bc / e").map { field.get(com.ibm.icu.text.RuleBasedCollator(it)) as SourceData }
        for(sd in sources) {
            val td=CollationDataOracleTest().importSource(sd)
            for(prefixes in listOf(false,true)) for(sink in listOf(false,true)) compare(sd,td,prefixes,null,sink,reuse=true)
            compare(sd,td,true,listOf(-1,0,48,65,97,98,99,0xe4,0x301,0xac00,0xac01,0xd800,0x1f600,0x10ffff,0x110000))
            for(stop in listOf(1,7,50)) compare(sd,td,true,null,stop=stop)
            compare(sd,td,true,null,withSets=false)
        }
    }
    @Test fun stringRangeHelpersAndNullSets() {
        val method=Source::class.java.getDeclaredMethod("addStrings",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,SourceSet::class.java).apply { isAccessible=true }
        for(start in listOf(-1,0,65,0xd800,0x1f600,0x10ffff,0x110000,Int.MAX_VALUE)) for(end in listOf(start,start-1)) for(present in listOf(false,true)) {
            val a=if(present) SourceSet() else null;val b=if(present) UnicodeSet() else null
            val source=Source(null,null,null,true);val target=ContractionsAndExpansions(null,null,null,true)
            assertEquals(outcome { method.invoke(source,start,end,a) },outcome { target.addStrings(start,end,b) },"addStrings/$start/$end/$present")
            assertEquals(a?.toPattern(false),b?.toPattern(false))
        }
    }
    @Test fun syntheticTagsFallbackAndPartialState() {
        val bridge=CollationDataOracleTest();val d=iteratorFixtureData();val sd=bridge.exportTarget(d)
        compare(sd,d,true,listOf(0,48,97,101,102,103,111,112,0xac00,0xac01),reuse=true)
        for(tag in 0..15) for(sink in listOf(false,true)) {
            val td=iteratorFixtureData().apply { trie=iteratorFixtureTrie(mapOf(65 to Collation.makeCE32FromTagIndexAndLength(tag,0,2))) }
            compare(bridge.exportTarget(td),td,true,listOf(65),sink)
        }
        val base=iteratorFixtureData().apply { trie=iteratorFixtureTrie((60..70).associateWith { Collation.makeCE32FromTagIndexAndLength(6,0,2) }) }
        val child=iteratorFixtureData().apply { this.base=base;trie=iteratorFixtureTrie((60..70).associateWith { if(it==65) iteratorSimple(it) else Collation.FALLBACK_CE32 }) }
        compare(bridge.exportTarget(child),child,true,null,reuse=true)
    }
}
