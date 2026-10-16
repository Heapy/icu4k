@file:Suppress("DEPRECATION")
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
import kotlin.test.*

class TimeZoneFormatParseCallbackOracleTest {
    private data class Record(val type: NameType,val id: String?,val meta: String?,val length: Int)
    private class Script {
        val trace = mutableListOf<String>()
        var names: List<Record>? = emptyList()
        var tzdb: List<Record>? = emptyList()
        var genericLength = 0
        var regionValid = true
        var virtualOffset = false
        var defaultCalls = 0
        fun reset() { trace.clear(); defaultCalls = 0 }
        fun sourceNames(tzdbService: Boolean): SNames = object : SNames() {
            override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
            override fun getAvailableMetaZoneIDs(tzID: String?): Set<String> = emptySet()
            override fun getMetaZoneID(tzID: String?,date: Long): String? = null
            override fun getReferenceZoneID(meta: String?,region: String?): String? { trace.add("reference:$meta:$region"); return if (regionValid) "America/Chicago" else null }
            override fun getMetaZoneDisplayName(id: String?,type: SNames.NameType?): String? = null
            override fun getTimeZoneDisplayName(id: String?,type: SNames.NameType?): String? = null
            override fun find(text: CharSequence?,start: Int,types: EnumSet<SNames.NameType>?): Collection<SNames.MatchInfo>? {
                trace.add("${if (tzdbService) "tzdb" else "names"}:"+types?.joinToString { it.name })
                return (if (tzdbService) tzdb else names)?.map { record -> object : SNames.MatchInfo(SNames.NameType.valueOf(record.type.name),record.id,record.meta,record.length) {
                    override fun matchLength(): Int { trace.add("length:${record.length}"); return super.matchLength() }
                    override fun nameType(): SNames.NameType { trace.add("type:${record.type}"); return super.nameType() }
                    override fun tzID(): String? { trace.add("zone:${record.id}"); return super.tzID() }
                    override fun mzID(): String? { trace.add("meta:${record.meta}"); return super.mzID() }
                } }
            }
        }
        fun targetMatches(tzdbService: Boolean,types: Set<NameType>): Collection<MatchInfo>? {
            trace.add("${if (tzdbService) "tzdb" else "names"}:"+types.joinToString { it.name })
            return (if (tzdbService) tzdb else names)?.map { record -> object : MatchInfo(record.type,record.id,record.meta,record.length) {
                override fun matchLength(): Int { trace.add("length:${record.length}"); return super.matchLength() }
                override fun nameType(): NameType { trace.add("type:${record.type}"); return super.nameType() }
                override fun tzID(): String? { trace.add("zone:${record.id}"); return super.tzID() }
                override fun mzID(): String? { trace.add("meta:${record.meta}"); return super.mzID() }
            } }
        }
        fun defaults(): Int { trace.add("defaults:${defaultCalls}"); return if (defaultCalls++ == 0) 2 else 1 }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun callbacksTieBreakingVirtualOffsetsAndLateMappingFailure() {
        val script = Script()
        val a = object : Source(com.ibm.icu.util.ULocale("en@rg=cnzzzz")) {
            override fun getDefaultParseOptions(): EnumSet<Source.ParseOption> = EnumSet.noneOf(Source.ParseOption::class.java).also { set ->
                val mask = script.defaults()
                Source.ParseOption.entries.forEachIndexed { i,t -> if (mask and (1 shl i) != 0) set.add(t) }
            }
            override fun parseOffsetLocalizedGMT(text: String?,pos: java.text.ParsePosition?): Int {
                script.trace.add("virtual:false")
                if (!script.virtualOffset) return super.parseOffsetLocalizedGMT(text,pos)
                pos!!.index += 2; return 123000
            }
            override fun parseOffsetShortLocalizedGMT(text: String?,pos: java.text.ParsePosition?): Int {
                script.trace.add("virtual:true")
                if (!script.virtualOffset) return super.parseOffsetShortLocalizedGMT(text,pos)
                pos!!.index += 2; return 123000
            }
        }
        val names = script.sourceNames(false)
        a.setTimeZoneNames(names)
        Source::class.java.getDeclaredField("_tzdbNames").also { it.isAccessible = true }.set(a,script.sourceNames(true))
        val constructor = SGeneric.GenericMatchInfo::class.java.getDeclaredConstructor(SGeneric.GenericNameType::class.java,String::class.java,Int::class.javaPrimitiveType,Source.TimeType::class.java).also { it.isAccessible = true }
        val generic = object : SGeneric(com.ibm.icu.util.ULocale("en"),names) {
            override fun findBestMatch(text: String?,start: Int,types: EnumSet<SGeneric.GenericNameType>?): SGeneric.GenericMatchInfo? {
                script.trace.add("generic:"+types?.joinToString { it.name })
                return if (script.genericLength == 0) null else constructor.newInstance(SGeneric.GenericNameType.LONG,"Europe/London",script.genericLength,Source.TimeType.DAYLIGHT)
            }
        }
        Source::class.java.getDeclaredField("_gnames").also { it.isAccessible = true }.set(a,generic)
        val owner = object : OwnerQueries<com.ibm.icu.util.TimeZone> {
            override fun findNames(text: String,start: Int,types: Set<NameType>) = script.targetMatches(false,types)
            override fun findTZDBNames(text: String,start: Int,types: Set<NameType>) = script.targetMatches(true,types)
            override fun findBestGenericMatch(text: String,start: Int,types: Set<GenericNameType>): GenericMatchInfo? {
                script.trace.add("generic:"+types.joinToString { it.name })
                return if (script.genericLength == 0) null else GenericMatchInfo("Europe/London",script.genericLength,TimeType.DAYLIGHT)
            }
            override fun getReferenceZoneID(metaZone: String?,region: String): String? { script.trace.add("reference:$metaZone:$region"); return if (script.regionValid) "America/Chicago" else null }
            override fun getTimeZone(id: String?) = com.ibm.icu.util.TimeZone.getTimeZone(id)
            override fun getCustomTimeZone(offset: Int) = com.ibm.icu.impl.ZoneMeta.getCustomTimeZone(offset)
            override fun getDefaultParseOptions(): Set<ParseOption> {
                val mask = script.defaults()
                return ParseOption.entries.filterIndexed { i,_ -> mask and (1 shl i) != 0 }.toSet()
            }
            override fun parseLocalizedOffset(core: LocalizedGMTOffsetCore,text: String,pos: ParsePosition,short: Boolean): Int {
                script.trace.add("virtual:$short")
                if (!script.virtualOffset) return core.parseOffsetLocalizedGMT(text,pos,short)
                pos.index += 2; return 123000
            }
        }
        val b = TimeZoneFormatParseCore(ULocale("en@rg=cnzzzz"),LocalizedGMTOffsetCore(ULocale("en@rg=cnzzzz")),owner)
        val choices = listOf(null,emptyList(),listOf(Record(NameType.LONG_STANDARD,null,"America_Central",3)),listOf(Record(NameType.SHORT_DAYLIGHT,"Asia/Kolkata",null,3),Record(NameType.EXEMPLAR_LOCATION,"Europe/Paris",null,5),Record(NameType.LONG_DAYLIGHT,"Asia/Tokyo",null,5)))
        for (records in choices) for (genericLength in listOf(0,3,7)) for (valid in listOf(false,true)) for (virtual in listOf(false,true)) {
            script.names = records; script.tzdb = records?.reversed(); script.genericLength = genericLength; script.regionValid = valid; script.virtualOffset = virtual
            for (style in Source.Style.entries) for (options in listOf(null,EnumSet.allOf(Source.ParseOption::class.java))) for (text in listOf("CSTabcdef suffix","GMT trailing","Z trailing","America/New_York suffix","unk suffix")) {
                val ap = java.text.ParsePosition(0).also { it.errorIndex = 73 }
                val bp = ParsePosition(0).also { it.errorIndex = 73 }
                val at = com.ibm.icu.util.Output<Source.TimeType>(Source.TimeType.STANDARD)
                val bt = Output(TimeType.STANDARD)
                script.reset()
                val expected = outcome { a.parse(style,text,ap,options,at)?.id }
                val trace = script.trace.toList()
                script.reset()
                assertEquals(expected,outcome { b.parse(Style.valueOf(style.name),text,bp,options?.map { ParseOption.valueOf(it.name) }?.toSet(),bt)?.id },"$style/$text/$records/$genericLength")
                assertEquals(listOf(ap.index,ap.errorIndex,at.value?.name),listOf(bp.index,bp.errorIndex,bt.value?.name))
                assertEquals(trace,script.trace,"trace/$style/$text/$records/$genericLength")
            }
        }
    }
}
