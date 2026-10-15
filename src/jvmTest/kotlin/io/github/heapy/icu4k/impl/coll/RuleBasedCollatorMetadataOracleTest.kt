// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.text.RuleBasedCollator as Source
import com.ibm.icu.text.UnicodeSet as SourceSet
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.Output
import kotlin.random.Random
import kotlin.test.*

class RuleBasedCollatorMetadataOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private data class Pairing(val s: Source, val t: CollationTailoring, val m: RuleBasedCollatorMetadata)
    private fun rules(rule: String): Pairing {
        val s=Source(rule); val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule)
        t.actualLocale=null
        return Pairing(s,t,collatorMetadata(t,null))
    }
    private fun locale(name: String): Pairing {
        val valid=Output<ULocale>(); val t=CollationLoader.loadTailoring(ULocale(name),valid)
        val s=com.ibm.icu.text.Collator.getInstance(SourceLocale(name)) as Source
        return Pairing(s,t,collatorMetadata(t,valid.value))
    }
    @Test fun equalityHashRulesAndTailoringIntrospection() {
        val all=listOf("", "&a<b<c", "&a < b < c", "&a<c<b", "&c<ch/e", "&a<é", "&a<ä", "[strength 1]&a<b<c", "[import de-u-co-phonebk]").map(::rules)+
            listOf("root","en","de@collation=phonebook","es@collation=traditional","ja","zh").map(::locale)
        for(a in all) {
            assertEquals(a.s.rules,a.m.getRules())
            assertEquals(a.s.getRules(true),a.m.getRules(true))
            assertEquals(a.s.tailoredSet.toPattern(false),a.m.getTailoredSet().toPattern(false))
            assertEquals(a.s.hashCode(),a.m.behaviorHashCode())
            assertEquals(a.s.version.toString(),a.m.getVersion().toString())
            assertEquals(a.s.ucaVersion.toString(),a.m.getUCAVersion().toString())
            for(b in all) assertEquals(a.s==b.s,a.m.hasSameBehavior(b.m),"${a.s.rules}/${b.s.rules}")
            for(prefix in listOf(false,true)) {
                val sc=SourceSet("[z]"); val se=SourceSet("[z]"); val tc=UnicodeSet("[z]"); val te=UnicodeSet("[z]")
                a.s.getContractionsAndExpansions(sc,se,prefix); a.m.getContractionsAndExpansions(tc,te,prefix)
                assertEquals(sc.toPattern(false),tc.toPattern(false)); assertEquals(se.toPattern(false),te.toPattern(false))
            }
        }
    }
    @Test fun versionsIncludingUnsignedOverflowAndLocaleMutations() {
        val p=rules("&a<b"); val sourceTailoring=field(p.s,"tailoring") as com.ibm.icu.impl.coll.CollationTailoring
        val original=sourceTailoring.version; val originalT=p.t.version
        val random=Random(78344)
        try {
            for(v in List(1500) { random.nextInt() } + listOf(0,Int.MIN_VALUE,Int.MAX_VALUE,-1)) {
                sourceTailoring.version=v; p.t.version=v
                assertEquals(outcome { p.s.version.toString() },outcome { p.m.getVersion().toString() })
                assertEquals(outcome { p.s.ucaVersion.toString() },outcome { p.m.getUCAVersion().toString() })
            }
        } finally { sourceTailoring.version=original; p.t.version=originalT }
        val setter=Source::class.java.getDeclaredMethod("setLocale",SourceLocale::class.java,SourceLocale::class.java).apply { isAccessible=true }
        for(pair in listOf(rules("&a<b"),locale("de_DE"),locale("de@collation=phonebook"))) {
            val savedActual=pair.t.actualLocale
            for((v,a) in listOf(null to null,"fr" to "fr","de_DE" to "de","en" to null,null to "en","en_US" to "en", "de" to "de")) {
                assertEquals(outcome { setter.invoke(pair.s,v?.let(::SourceLocale),a?.let(::SourceLocale)); Unit },outcome { pair.m.setLocale(v?.let(::ULocale),a?.let(::ULocale)) })
                assertEquals(pair.s.getLocale(SourceLocale.VALID_LOCALE)?.name,pair.m.getLocale(ULocale.VALID_LOCALE)?.getName())
                assertEquals(pair.s.getLocale(SourceLocale.ACTUAL_LOCALE)?.name,pair.m.getLocale(ULocale.ACTUAL_LOCALE)?.getName())
                assertEquals(savedActual,pair.t.actualLocale)
            }
            assertEquals(outcome { pair.s.getLocale(null) },outcome { pair.m.getLocale(null) })
        }
    }
    @Test fun cacheSharingClearOrderAndInternalContractions() {
        val p=rules("&c<ch/e<cu"); val m=p.m
        val init=Source::class.java.getDeclaredMethod("initMaxExpansions").apply { isAccessible=true }
        init.invoke(p.s); m.initMaxExpansions()
        val sourceTailoring=field(p.s,"tailoring") as com.ibm.icu.impl.coll.CollationTailoring
        assertEquals(sourceTailoring.maxExpansions,p.t.maxExpansions)
        val cached=p.t.maxExpansions
        val other=collatorMetadata(p.t)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(6)
        try {
            val jobs=(0..<60).map { pool.submit { other.initMaxExpansions(); assertSame(cached,p.t.maxExpansions) } }
            for(job in jobs) job.get(30,java.util.concurrent.TimeUnit.SECONDS)
        } finally { pool.shutdownNow() }
        // Race the first initialization across separate owners of one tailoring.
        val fresh=rules("&c<ch/e<cu")
        assertNull(fresh.t.maxExpansions)
        val owners=List(6) { collatorMetadata(fresh.t) }
        val start=java.util.concurrent.CountDownLatch(1)
        val racers=java.util.concurrent.Executors.newFixedThreadPool(6)
        try {
            val jobs=owners.map { owner -> racers.submit<java.util.Map<*,*>?> {
                start.await(); owner.initMaxExpansions()
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
                (fresh.t.maxExpansions as java.util.Map<*,*>?)
            } }
            start.countDown()
            val maps=jobs.map { it.get(30,java.util.concurrent.TimeUnit.SECONDS) }
            for(map in maps) assertSame(maps.first(),map)
            assertEquals(sourceTailoring.maxExpansions,fresh.t.maxExpansions)
        } finally { racers.shutdownNow() }
        val method=Source::class.java.getDeclaredMethod("internalAddContractions",Int::class.javaPrimitiveType,SourceSet::class.java).apply { isAccessible=true }
        for(c in listOf(0,'c'.code,'x'.code,0x10000)) {
            val s=SourceSet("[z]"); val t=UnicodeSet("[z]")
            method.invoke(p.s,c,s); m.internalAddContractions(c,t)
            assertEquals(s.toPattern(false),t.toPattern(false))
        }
        val sc=SourceSet("[z]"); val se=SourceSet("[x]").freeze(); val tc=UnicodeSet("[z]"); val te=UnicodeSet("[x]").freeze()
        assertEquals(outcome { p.s.getContractionsAndExpansions(sc,se,false) },outcome { m.getContractionsAndExpansions(tc,te,false) })
        assertEquals(sc.toPattern(false),tc.toPattern(false)); assertEquals(se.toPattern(false),te.toPattern(false))
    }
}
