@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.TimeZoneGenericNames as Source
import com.ibm.icu.text.TimeZoneNames as SNames
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TimeZoneGenericNamesDSTOracleTest {
    private class Script {
        val trace = mutableListOf<String>()
        var now = 0L
        var dst = 0
        var beforeDelta: Int? = null
        var afterDelta: Int? = null
        var direct: String? = null
        var standard: String? = null
        var golden: String? = null
        var virtualLocation = false
        fun offset(date: Long,local: Boolean,values: IntArray) {
            trace.add("offset:$date:$local:${values.toList()}")
            values[0] = -18_000_000
            values[1] = when(date) { now -> dst; now-RANGE -> if (beforeDelta != null && beforeDelta!! > 0) 3_600_000 else 0; else -> if (afterDelta != null && afterDelta!! < 0) -3_600_000 else 0 }
        }
        fun transition(previous: Boolean,base: Long,inclusive: Boolean): com.ibm.icu.util.TimeZoneTransition? {
            trace.add("transition:$previous:$base:$inclusive")
            val delta = (if (previous) beforeDelta else afterDelta) ?: return null
            val value = if (previous) now-RANGE+delta else now+RANGE+delta
            val fromRule = object : com.ibm.icu.util.InitialTimeZoneRule("from",0,0) {
                override fun getDSTSavings(): Int { trace.add("fromDst"); return -3_600_000 }
            }
            val toRule = object : com.ibm.icu.util.InitialTimeZoneRule("to",0,0) {
                override fun getDSTSavings(): Int { trace.add("toDst"); return 3_600_000 }
            }
            return object : com.ibm.icu.util.TimeZoneTransition(value,fromRule,toRule) {
                override fun getTime(): Long { trace.add("time:$value"); return value }
                override fun getFrom(): com.ibm.icu.util.TimeZoneRule { trace.add("from"); return fromRule }
                override fun getTo(): com.ibm.icu.util.TimeZoneRule { trace.add("to"); return toRule }
            }
        }
        val names = object : SNames() {
            override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
            override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
            override fun getMetaZoneID(id: String?,date: Long): String { trace.add("meta:$id:$date"); return "America_Eastern" }
            override fun getReferenceZoneID(id: String?,region: String?): String? { trace.add("reference:$id:$region"); return golden }
            override fun getMetaZoneDisplayName(id: String?,type: SNames.NameType?): String? { trace.add("metaName:$id:$type"); return if (type == SNames.NameType.LONG_GENERIC || type == SNames.NameType.SHORT_GENERIC) "generic" else standard }
            override fun getTimeZoneDisplayName(id: String?,type: SNames.NameType?): String? { trace.add("zoneName:$id:$type"); return if (type == SNames.NameType.LONG_GENERIC || type == SNames.NameType.SHORT_GENERIC) direct else standard }
            override fun getExemplarLocationName(id: String?): String { trace.add("city:$id"); return "city" }
        }
        val basic = object : com.ibm.icu.util.RuleBasedTimeZone("America/New_York",com.ibm.icu.util.InitialTimeZoneRule("initial",0,0)) {
            override fun getID(): String { trace.add("id"); return "America/New_York" }
            override fun getOffset(date: Long,local: Boolean,values: IntArray) = offset(date,local,values)
            override fun getPreviousTransition(base: Long,inclusive: Boolean) = transition(true,base,inclusive)
            override fun getNextTransition(base: Long,inclusive: Boolean) = transition(false,base,inclusive)
        }
        val nonBasic = object : com.ibm.icu.util.TimeZone() {
            override fun getID(): String { trace.add("id"); return "America/New_York" }
            override fun getOffset(date: Long,local: Boolean,values: IntArray) = offset(date,local,values)
            override fun getOffset(era: Int,year: Int,month: Int,day: Int,dow: Int,millis: Int) = error("Unused field-offset overload")
            override fun setRawOffset(offset: Int) = error("Unused mutation")
            override fun getRawOffset() = error("Unused raw-offset query")
            override fun useDaylightTime() = error("Unused daylight query")
            override fun inDaylightTime(date: java.util.Date?) = error("Unused date daylight query")
        }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is VirtualMachineError -> throw e; is NullPointerException -> "null"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun transitionBoundariesNonBasicSamplingAndVirtualLocation() {
        val script = Script()
        val a = object : Source(com.ibm.icu.util.ULocale("en@rg=cnzzzz"),script.names) {
            override fun getGenericLocationName(id: String?): String? {
                script.trace.add("location:$id")
                return if (script.virtualLocation) "virtual location" else super.getGenericLocationName(id)
            }
        }
        val names = GenericNamesOracleSupport.names(script.names)
        val owner = object : GenericNamesOracleSupport.Owner(names) {
            override fun getGenericLocationName(core: TimeZoneGenericNamesCore<com.ibm.icu.util.TimeZone>,id: String): String? {
                script.trace.add("location:$id")
                return if (script.virtualLocation) "virtual location" else core.getGenericLocationName(id)
            }
        }
        val b = TimeZoneGenericNamesCore(ULocale("en@rg=cnzzzz"),names,owner)
        for (zone in listOf(script.basic,script.nonBasic)) for (dst in listOf(0,3_600_000,-3_600_000)) for (before in listOf(null,-1,0,1)) for (after in listOf(null,-1,0,1)) {
            script.dst = dst; script.beforeDelta = before; script.afterDelta = after
            for (standard in listOf(null,"generic","GENERIC","standard","")) for (direct in listOf(null,"direct","")) for (golden in listOf(null,"America/New_York","America/Los_Angeles")) {
                script.standard = standard; script.direct = direct; script.golden = golden
                for (date in listOf(Long.MIN_VALUE,0L,Long.MAX_VALUE)) for (type in Source.GenericNameType.entries) {
                    script.now = date
                    script.virtualLocation = before == 1
                    script.trace.clear()
                    val expected = outcome { a.getDisplayName(zone,type,date) }
                    val trace = script.trace.toList()
                    script.trace.clear()
                    assertEquals(expected,outcome { b.getDisplayName(zone,GenericNameType.valueOf(type.name),date) },"$type/$date/$dst/$before/$after/$direct/$standard/$golden")
                    assertEquals(trace,script.trace,"trace/$type/$date/$dst/$before/$after/$direct/$standard/$golden")
                }
            }
        }
    }
    private companion object { const val RANGE = 184L*24*60*60*1000 }
}
