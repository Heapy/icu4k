@file:Suppress("DEPRECATION","UNCHECKED_CAST")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.TimeZoneGenericNames as Source
import com.ibm.icu.impl.TimeZoneNamesImpl as SNames
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.util.*
import java.util.EnumSet
import kotlin.random.Random
import kotlin.test.*

class TimeZoneGenericNamesCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is VirtualMachineError -> throw e; is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private data class Pairing(val a: Source,val b: TimeZoneGenericNamesCore<com.ibm.icu.util.TimeZone>)
    private fun pair(locale: String): Pairing {
        val a = Source(com.ibm.icu.util.ULocale(locale),SNames(com.ibm.icu.util.ULocale(locale)))
        val names = GenericNamesOracleSupport.names(TimeZoneNamesDataCore(ULocale(locale),{ com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(com.ibm.icu.util.TimeZone.getDefault()) }))
        return Pairing(a,TimeZoneGenericNamesCore(ULocale(locale),names,GenericNamesOracleSupport.Owner(names)))
    }
    private fun sourceMatch(match: Source.GenericMatchInfo?) = match?.let { listOf(it.nameType()?.name,it.tzID(),it.matchLength(),it.timeType()?.name) }
    private fun targetMatch(match: TimeZoneGenericNamesCore.GenericMatchInfo?) = match?.let { listOf(it.nameType()?.name,it.tzID(),it.matchLength(),it.timeType()?.name) }
    private fun compare(pair: Pairing,text: String?,start: Int,types: EnumSet<Source.GenericNameType>?) {
        val filter = types?.map { GenericNameType.valueOf(it.name) }?.toSet()
        assertEquals(outcome { sourceMatch(pair.a.findBestMatch(text,start,types)) },outcome { targetMatch(pair.b.findBestMatch(text,start,filter)) },"best/$text/$start/$types")
        assertEquals(outcome { pair.a.find(text,start,types)?.map(::sourceMatch) },outcome { pair.b.find(text,start,filter)?.map(::targetMatch) },"all/$text/$start/$types")
    }
    private fun field(value: Any,name: String): Any? = value.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(value)
    private fun state(pair: Pairing) {
        assertEquals(field(pair.a,"_gnamesTrieFullyLoaded"),field(pair.b,"trieFullyLoaded"))
        assertEquals(field(pair.a,"_gnamesTrie") == null,field(pair.b,"trie") == null)
        for ((a,b) in listOf("_genericLocationNamesMap" to "locationNames","_genericPartialLocationNamesMap" to "partialNames")) {
            val source = field(pair.a,a) as Map<String,String>
            val target = field(pair.b,b) as GenericNameMap
            assertEquals(source.isEmpty(),target.isEmpty())
            source.forEach { (key,value) -> assertEquals(value,target.get(key),key) }
        }
    }
    @Test fun allLocalesRepresentativeLocationsAndAllRegionBytes() {
        val files = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_REGION_BASE_NAME+"/") { files.add(it) }
        assertEquals(544,files.size)
        for (file in files) {
            val path = "region/$file"
            val expected = com.ibm.icu.impl.ICUData.getRequiredStream("/"+com.ibm.icu.impl.ICUData.ICU_BASE_NAME+"/"+path).use { it.readBytes() }
            val actual = assertNotNull(ICUData.getBytes(path))
            assertContentEquals(expected,actual,path)
            actual.fill(0)
            assertContentEquals(expected,ICUData.getBytes(path),path)
        }
        for (locale in files.filter { it != "pool.res" && it != "res_index.res" }.map { it.removeSuffix(".res") }) {
            val pair = pair(locale)
            for (id in listOf(null,"","America/Los_Angeles","America/New_York","Europe/London","Europe/Minsk","Asia/Kolkata","Asia/Tokyo","Etc/UTC","Etc/Unknown","CST6CDT","missing")) {
                assertEquals(outcome { pair.a.getGenericLocationName(id) },outcome { pair.b.getGenericLocationName(id) },"$locale/$id")
            }
            state(pair)
        }
    }
    @Test fun everyZoneSeasonAndType() {
        val ids = com.ibm.icu.util.TimeZone.getAvailableIDs().toList()+listOf("Etc/Unknown","GMT+05:30","unknown")
        for (locale in listOf("en_US","en_GB","fr","ar","zh_Hant","he","und_ZZ")) {
            val pair = pair(locale)
            for (id in ids) {
                assertEquals(outcome { pair.a.getGenericLocationName(id) },outcome { pair.b.getGenericLocationName(id) },"location/$locale/$id")
                val zone = com.ibm.icu.util.TimeZone.getTimeZone(id)
                for (date in listOf(-2_208_988_800_000L,0L,1_704_067_200_000L,1_719_792_000_000L)) for (type in Source.GenericNameType.entries) {
                    assertEquals(outcome { pair.a.getDisplayName(zone,type,date) },outcome { pair.b.getDisplayName(zone,GenericNameType.valueOf(type.name),date) },"$locale/$id/$date/$type")
                }
            }
            state(pair)
            for (type in Source.GenericNameType.entries+listOf(null)) for (date in listOf(Long.MIN_VALUE,Long.MAX_VALUE)) {
                for (zone in listOf(null,com.ibm.icu.util.TimeZone.getTimeZone("Europe/London"))) assertEquals(outcome { pair.a.getDisplayName(zone,type,date) },outcome { pair.b.getDisplayName(zone,type?.let { GenericNameType.valueOf(it.name) },date) })
            }
        }
    }
    @Test fun parsingTypesLocalTieBreaksAndCacheLoading() {
        for (locale in listOf("en_US","en_GB","fr","ar","zh_Hant","he")) {
            val pair = pair(locale)
            val inputs = linkedSetOf("missing","Eastern Standard Time","Eastern Time","China Time","PT","CST","IST","United Kingdom Time","Los Angeles Time")
            for (id in listOf("America/Los_Angeles","Europe/London","Asia/Kolkata","America/Indiana/Knox")) {
                pair.a.getGenericLocationName(id)?.let(inputs::add)
                pair.b.getGenericLocationName(id)
                for (type in Source.GenericNameType.entries) pair.a.getDisplayName(com.ibm.icu.util.TimeZone.getTimeZone(id),type,0)?.let(inputs::add)
                for (type in GenericNameType.entries) pair.b.getDisplayName(com.ibm.icu.util.TimeZone.getTimeZone(id),type,0)
            }
            for (input in inputs) for (mask in 0..7) {
                val types = EnumSet.noneOf(Source.GenericNameType::class.java).also { set -> Source.GenericNameType.entries.forEachIndexed { i,t -> if (mask and (1 shl i) != 0) set.add(t) } }
                compare(pair,input,0,types)
                compare(pair,"!${input.lowercase()} suffix",1,types)
            }
            for (text in listOf(null,"","x","CST","Los Angeles Time")) for (start in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) compare(pair,text,start,null)
            state(pair)
        }
    }
    @Test fun patternsFailureStateClonesAndEnumFallback() {
        for (type in Source.GenericNameType.entries) for (other in Source.GenericNameType.entries+listOf(null)) assertEquals(outcome { type.isFallbackTypeOf(other) },outcome { GenericNameType.valueOf(type.name).isFallbackTypeOf(other?.let { GenericNameType.valueOf(it.name) }) })
        val random = Random(0x3761)
        val patterns = mutableListOf<String?>(null,"","X{0}","{1} ({0})","'{0}'","''{0}''","{0,number}","{0,date,full}","{0,choice,0#none|1#{1}}","{0","{1,unknown}")
        repeat(35) { patterns.add(buildString { repeat(random.nextInt(14)) { append("ab{}01,'"[random.nextInt(8)]) } }) }
        for (pattern in patterns) for (type in Source.Pattern.entries+listOf(null)) {
            val pair = pair("en_US")
            val clone = Pairing(pair.a.freeze().cloneAsThawed(),pair.b.freeze().cloneAsThawed())
            assertFalse(clone.b.isFrozen())
            assertEquals(outcome { clone.a.setFormatPattern(type,pattern); Unit },outcome { clone.b.setFormatPattern(type?.let { TimeZoneGenericNamesCore.Pattern.valueOf(it.name) },pattern); Unit },"$type/$pattern")
            for (id in listOf("America/Los_Angeles","Europe/London")) {
                assertEquals(outcome { clone.a.getGenericLocationName(id) },outcome { clone.b.getGenericLocationName(id) })
                assertEquals(outcome { clone.a.getGenericLocationName(id) },outcome { clone.b.getGenericLocationName(id) })
                assertEquals(outcome { pair.a.getGenericLocationName(id) },outcome { pair.b.getGenericLocationName(id) })
            }
            compare(clone,"Los Angeles Time",0,EnumSet.allOf(Source.GenericNameType::class.java))
            state(clone); state(pair)
            assertEquals(outcome { pair.a.setFormatPattern(null,null) },outcome { pair.b.setFormatPattern(null,null) })
        }
    }
}
