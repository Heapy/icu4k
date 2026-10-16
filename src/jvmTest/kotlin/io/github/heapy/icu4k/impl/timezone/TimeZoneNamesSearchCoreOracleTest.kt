@file:Suppress("DEPRECATION","UNCHECKED_CAST")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.TimeZoneNamesImpl as Source
import com.ibm.icu.text.TimeZoneNames.NameType as SType
import com.ibm.icu.text.TimeZoneNames.MatchInfo as SMatch
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.ICUData
import java.util.EnumSet
import kotlin.random.Random
import kotlin.test.*

class TimeZoneNamesSearchCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun defaultZone(): String? = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(com.ibm.icu.util.TimeZone.getDefault())
    private fun pair(locale: String) = Source(com.ibm.icu.util.ULocale(locale)) to TimeZoneNamesDataCore(ULocale(locale),::defaultZone)
    private fun field(a: Any,name: String): Any? = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name == name }.also { it.isAccessible = true }.get(a)
    private fun type(t: SType) = TimeZoneNamesDataCore.NameType.valueOf(t.name)
    private fun sourceMatches(matches: Collection<SMatch>) = matches.map { listOf(it.nameType().name,it.tzID(),it.mzID(),it.matchLength()) }
    private fun targetMatches(matches: Collection<TimeZoneNamesDataCore.MatchInfo>) = matches.map { listOf(it.nameType().name,it.tzID(),it.mzID(),it.matchLength()) }
    private fun compare(a: Source,b: TimeZoneNamesDataCore,text: CharSequence?,start: Int,types: EnumSet<SType>?) {
        assertEquals(outcome { sourceMatches(a.find(text,start,types)) },outcome { targetMatches(b.find(text,start,types?.map(::type)?.toSet())) },"$text/$start/$types")
    }
    private fun state(a: Source,b: TimeZoneNamesDataCore) {
        assertEquals(field(a,"_namesFullyLoaded"),field(b,"namesFullyLoaded"))
        assertEquals(field(a,"_namesTrieFullyLoaded"),field(b,"namesTrieFullyLoaded"))
        for ((source,target) in listOf("_tzNamesMap" to "zoneNames","_mzNamesMap" to "metaNames")) {
            val x = field(a,source) as Map<String,Any>
            val y = field(b,target) as Map<String,Any>
            assertEquals(x.keys.toList(),y.keys.toList(),source)
            for(key in x.keys) assertEquals(field(x[key]!!,"didAddIntoTrie"),field(y[key]!!,"didAddIntoTrie"),key)
        }
    }
    @Test fun lazyThreePassLoadingAndBulkOnlyStatesMatch() {
        for(locale in listOf("en","fr","ru","en_GB","zh_Hant","ar")) {
            val (a,b) = pair(locale)
            state(a,b)
            val city = a.getExemplarLocationName("America/Los_Angeles")
            assertEquals(city,b.getExemplarLocationName("America/Los_Angeles"))
            compare(a,b,city,0,EnumSet.of(SType.EXEMPLAR_LOCATION)); state(a,b)
            compare(a,b,city+" suffix",0,EnumSet.of(SType.EXEMPLAR_LOCATION)); state(a,b)
            compare(a,b,"Pacific Standard Time",0,EnumSet.noneOf(SType::class.java)); state(a,b)
            val (x,y) = pair(locale)
            x.loadAllDisplayNames(); y.loadAllDisplayNames(); state(x,y)
            compare(x,y,city,0,null); state(x,y)
        }
    }
    @Test fun everyLocaleParsingNamesAndOffsetsMatchOrderedResults() {
        val files = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_ZONE_BASE_NAME+"/") { files.add(it) }
        for(locale in files.filter { it != "pool.res" && it != "res_index.res" }.map { it.removeSuffix(".res") }) {
            val (a,b) = pair(locale)
            val texts = mutableSetOf("not a time zone 0123456789","GMT","PST","CST")
            for(id in listOf("America/Los_Angeles","America/New_York","Europe/London","Asia/Shanghai","Europe/Minsk")) {
                for(t in listOf(SType.EXEMPLAR_LOCATION,SType.LONG_STANDARD,SType.SHORT_STANDARD,SType.LONG_GENERIC)) {
                    val name = a.getDisplayName(id,t,0)
                    assertEquals(name,b.getDisplayName(id,type(t),0))
                    if (name != null) texts.add(name)
                }
            }
            for(text in texts) {
                compare(a,b,text,0,null)
                compare(a,b,"xx"+text+" trailing",2,null)
            }
            state(a,b)
        }
    }
    @Test fun allTypeSubsetsAndUnicodeCaseFoldingMatch() {
        for(locale in listOf("en","fr","ru","zh","ar")) {
            val (a,b) = pair(locale)
            for(mask in 0 until 128) {
                val types = EnumSet.noneOf(SType::class.java)
                for(t in SType.entries) if(mask and (1 shl t.ordinal) != 0) types.add(t)
                for(text in listOf("Pacific Standard Time","pAcIfIc StAnDaRd TiMe!","GMT","CST","New York","Нью-Йорк","Straße","İstanbul")) compare(a,b,text,0,types)
            }
        }
    }
    @Test fun mixedLoadFormatAndFindHistoriesKeepTrieAndCacheState() {
        val random = Random(7891)
        for(locale in listOf("en","fr_CA","ru","zh_Hant","ar")) {
            val (a,b) = pair(locale)
            repeat(80) { step ->
                val id = listOf("America/Los_Angeles","Europe/London","Unknown/Extra_City","Area/City$step","Etc/UTC","Asia/Tokyo").random(random)
                when(random.nextInt(4)) {
                    0 -> assertEquals(a.getExemplarLocationName(id),b.getExemplarLocationName(id))
                    1 -> { val t = SType.entries.random(random); assertEquals(a.getDisplayName(id,t,0),b.getDisplayName(id,type(t),0)) }
                    2 -> { a.loadAllDisplayNames(); b.loadAllDisplayNames() }
                    3 -> compare(a,b,TimeZoneNamesDataCore.getDefaultExemplarLocationName(id) ?: "GMT",0,null)
                }
                state(a,b)
            }
        }
    }
    @Test fun matchValidationInputErrorsAndMutableResultsMatch() {
        for(t in SType.entries+listOf(null)) for(zone in listOf(null,"","zone")) for(meta in listOf(null,"","meta")) for(length in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) {
            assertEquals(outcome { val m = SMatch(t,zone,meta,length); listOf(m.nameType().name,m.tzID(),m.mzID(),m.matchLength()) },
                outcome { val m = TimeZoneNamesDataCore.MatchInfo(t?.let(::type),zone,meta,length); listOf(m.nameType().name,m.tzID(),m.mzID(),m.matchLength()) })
        }
        val (a,b) = pair("en")
        for(text in listOf(null,"","abc")) for(start in listOf(Int.MIN_VALUE,-1,0,1,3,Int.MAX_VALUE)) compare(a,b,text,start,null)
        for(text in listOf("Pacific Standard Time","no match 0123456789")) {
            val x = a.find(text,0,null) as MutableCollection<SMatch>
            val y = b.find(text,0,null) as MutableCollection<TimeZoneNamesDataCore.MatchInfo>
            assertEquals(outcome { x.add(SMatch(SType.LONG_STANDARD,"test",null,1)) },outcome { y.add(TimeZoneNamesDataCore.MatchInfo(type(SType.LONG_STANDARD),"test",null,1)) })
            assertEquals(outcome { x.clear(); Unit },outcome { y.clear(); Unit })
            compare(a,b,text,0,null)
        }
    }

    private class CallbackText(private val value: String,private val callback: () -> Unit) : CharSequence {
        private var called = false
        val reads = mutableListOf<String>()
        override val length: Int get() { reads.add("length"); return value.length }
        override fun get(index: Int): Char {
            reads.add("get:$index")
            if(!called) { called = true; callback() }
            return value[index]
        }
        override fun subSequence(startIndex: Int,endIndex: Int): CharSequence = value.subSequence(startIndex,endIndex)
        override fun toString(): String = value
    }
    @Test fun typeFiltersStayLiveDuringTextCallbacks() {
        val (a,b) = pair("en")
        val x = EnumSet.allOf(SType::class.java)
        val y = TimeZoneNamesDataCore.NameType.entries.toMutableSet()
        val textA = CallbackText("Pacific Standard Time") { x.remove(SType.LONG_STANDARD) }
        val textB = CallbackText("Pacific Standard Time") { y.remove(type(SType.LONG_STANDARD)) }
        assertEquals(sourceMatches(a.find(textA,0,x)),targetMatches(b.find(textB,0,y)))
        assertEquals(textA.reads,textB.reads)
        state(a,b)
    }
    @Test fun textCallbacksCanReenterFormattingWithoutLosingPendingNames() {
        val (a,b) = pair("en")
        a.getExemplarLocationName("Area/Seed"); b.getExemplarLocationName("Area/Seed")
        val textA = CallbackText("Seed") { a.getExemplarLocationName("Area/Callback_City") }
        val textB = CallbackText("Seed") { b.getExemplarLocationName("Area/Callback_City") }
        assertEquals(sourceMatches(a.find(textA,0,EnumSet.of(SType.EXEMPLAR_LOCATION))),targetMatches(b.find(textB,0,setOf(type(SType.EXEMPLAR_LOCATION)))))
        assertEquals(textA.reads,textB.reads)
        state(a,b)
        compare(a,b,"Callback City",0,EnumSet.of(SType.EXEMPLAR_LOCATION))
        state(a,b)
    }

    @Test fun supplementaryNamesCaseFoldAndPrefixOffsetsMatch() {
        val (a,b) = pair("en")
        for(name in listOf("\uD801\uDC00","Straße","İstanbul","Σίσυφος")) {
            assertEquals(a.getExemplarLocationName("Area/$name"),b.getExemplarLocationName("Area/$name"))
        }
        for(text in listOf("\uD801\uDC28","xx\uD801\uDC28suffix","STRASSE","straße","İSTANBUL","σίσυφοσ")) {
            compare(a,b,text,if(text.startsWith("xx")) 2 else 0,EnumSet.of(SType.EXEMPLAR_LOCATION))
        }
        state(a,b)
    }
}
