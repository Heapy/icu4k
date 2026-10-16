@file:Suppress("DEPRECATION","UNCHECKED_CAST")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.text.TimeZoneNames as SNames
import com.ibm.icu.impl.TimeZoneGenericNames as SGeneric
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import java.util.EnumSet
import kotlin.random.Random
import kotlin.test.*

class TimeZoneFormatParseCoreOracleTest {
    private val genericGetter = Source::class.java.getDeclaredMethod("getTimeZoneGenericNames").also { it.isAccessible = true }
    private fun source(locale: String): Source = object : Source(com.ibm.icu.util.ULocale(locale)) { }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun normalize(zone: com.ibm.icu.util.TimeZone?): Any? = zone?.let { listOf(it.id,it.rawOffset,it.isFrozen,it.javaClass.name) }
    private class Pairing(val a: Source,val b: TimeZoneFormatParseCore<com.ibm.icu.util.TimeZone>)
    private fun pair(locale: String): Pairing {
        val a = source(locale)
        val sourceNames = com.ibm.icu.impl.TimeZoneNamesImpl(com.ibm.icu.util.ULocale(locale))
        a.setTimeZoneNames(sourceNames)
        val names = TimeZoneNamesDataCore(ULocale(locale),{ com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(com.ibm.icu.util.TimeZone.getDefault()) })
        // Equalize complete data before testing the selection policy; lazy names are tested separately.
        sourceNames.find("a definitely missing name",0,null)
        names.find("a definitely missing name",0,null)
        val tzdb = TZDBTimeZoneNamesCore(ULocale(locale))
        val owner = object : OwnerQueries<com.ibm.icu.util.TimeZone> {
            override fun findNames(text: String,start: Int,types: Set<NameType>) = names.find(text,start,types)
            override fun findTZDBNames(text: String,start: Int,types: Set<NameType>) = tzdb.find(text,start,types)
            override fun findBestGenericMatch(text: String,start: Int,types: Set<GenericNameType>): GenericMatchInfo? {
                val filter = EnumSet.noneOf(SGeneric.GenericNameType::class.java)
                types.forEach { filter.add(SGeneric.GenericNameType.valueOf(it.name)) }
                val match = (genericGetter.invoke(a) as SGeneric).findBestMatch(text,start,filter) ?: return null
                return GenericMatchInfo(match.tzID(),match.matchLength(),match.timeType()?.let { TimeType.valueOf(it.name) })
            }
            override fun getReferenceZoneID(metaZone: String?,region: String) = names.getReferenceZoneID(metaZone,region)
            override fun getTimeZone(id: String?) = com.ibm.icu.util.TimeZone.getTimeZone(id)
            override fun getCustomTimeZone(offset: Int) = com.ibm.icu.impl.ZoneMeta.getCustomTimeZone(offset)
            override fun getDefaultParseOptions() = a.defaultParseOptions.map { ParseOption.valueOf(it.name) }.toSet()
        }
        return Pairing(a,TimeZoneFormatParseCore(ULocale(locale),LocalizedGMTOffsetCore(ULocale(locale)),owner))
    }
    private fun compare(pair: Pairing,style: Source.Style?,text: String?,start: Int,options: EnumSet<Source.ParseOption>?,error: Int = -1) {
        val a = java.text.ParsePosition(start).also { it.errorIndex = error }
        val b = ParsePosition(start).also { it.errorIndex = error }
        val at = com.ibm.icu.util.Output<Source.TimeType>(Source.TimeType.DAYLIGHT)
        val bt = Output(TimeType.DAYLIGHT)
        assertEquals(outcome { normalize(pair.a.parse(style,text,a,options,at)) },outcome { normalize(pair.b.parse(style?.let { Style.valueOf(it.name) },text,b,options?.map { ParseOption.valueOf(it.name) }?.toSet(),bt)) },"$style/$text/$start/$options")
        assertEquals(listOf(a.index,a.errorIndex,at.value?.name),listOf(b.index,b.errorIndex,bt.value?.name),"state/$style/$text/$start/$options")
    }
    @Test fun allStylesOptionsAndRealNames() {
        for (locale in listOf("en_US","en_GB","zh","he","fr","ar","und_ZZ")) {
            val pair = pair(locale)
            val inputs = linkedSetOf("CST","IST","EST","GMT","GMT trailing","UTC","UT+bad","Z","Zulu","Z trailing","+053045","+1:23","GMT+12:60","America/New_York","uslax","unk","Los Angeles","Minsk","missing")
            for (id in listOf("America/Los_Angeles","Europe/London","Asia/Kolkata")) for (date in listOf(0L,1_719_792_000_000L)) for (style in Source.Style.entries) {
                inputs.add(pair.a.format(style,com.ibm.icu.util.TimeZone.getTimeZone(id),date))
            }
            val options = (0..3).map { mask -> EnumSet.noneOf(Source.ParseOption::class.java).also { set -> Source.ParseOption.entries.forEachIndexed { i,t -> if (mask and (1 shl i) != 0) set.add(t) } } } + listOf(null)
            for (style in Source.Style.entries) for (option in options) for (text in inputs) {
                compare(pair,style,text,0,option)
                compare(pair,style,"!${text.lowercase()} suffix",1,option,73)
            }
        }
    }
    @Test fun malformedInputsAndDefaultOptions() {
        val pair = pair("en_US")
        val random = Random(0x1426)
        val chars = "0123456789:+-GMTUTCZabc /١２\uD800\uDC00"
        repeat(1800) {
            val text = buildString { repeat(random.nextInt(16)) { append(chars[random.nextInt(chars.length)]) } }
            compare(pair,Source.Style.entries[random.nextInt(Source.Style.entries.size)],text,random.nextInt(-1,text.length+2),if (random.nextBoolean()) null else EnumSet.allOf(Source.ParseOption::class.java),19)
        }
        for (mask in 0..3) {
            pair.a.setDefaultParseOptions(EnumSet.noneOf(Source.ParseOption::class.java).also { set -> Source.ParseOption.entries.forEachIndexed { i,t -> if (mask and (1 shl i) != 0) set.add(t) } })
            for (style in Source.Style.entries + listOf(null)) for (text in listOf(null,"","CST","GMT+3","America/New_York","unk","Z trailing")) for (start in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) compare(pair,style,text,start,null,31)
        }
        val at = com.ibm.icu.util.Output<Source.TimeType>(Source.TimeType.DAYLIGHT)
        val bt = Output(TimeType.DAYLIGHT)
        assertEquals(outcome { pair.a.parse(null,null,null,null,at) },outcome { pair.b.parse(null,null,null,null,bt) })
        assertEquals(at.value.name,bt.value?.name)
    }
}
