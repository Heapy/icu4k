@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.text.TimeZoneNames as SNames
import com.ibm.icu.impl.TimeZoneGenericNames as SGeneric
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneFormatDisplayCallbackOracleTest {
    private class Script {
        val trace = mutableListOf<String>()
        var id: String? = "America/New_York"
        var raw = 19_800_000
        var dst = 0
        var daylight = false
        var name: String? = null
        var metaName: String? = null
        var city: String? = null
        var unknownCity: String? = null
        var generic: String? = null
        var nullNames = false
        var virtual = 0
        var fail: String? = null
        var captured: IntArray? = null
        var nameQueries = 0
        fun reset() { trace.clear(); captured = null; nameQueries = 0 }
        fun log(event: String) {
            trace.add(event)
            if (event.substringBefore(':') == fail) throw IllegalStateException("failure:$fail")
        }
        val names = object : SNames() {
            override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
            override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
            override fun getReferenceZoneID(id: String?,region: String?): String? = null
            override fun getMetaZoneID(id: String?,date: Long): String? { log("meta:$id:$date"); return "meta" }
            override fun getMetaZoneDisplayName(id: String?,type: SNames.NameType?): String? { log("metaName:$id:$type"); return metaName }
            override fun getTimeZoneDisplayName(id: String?,type: SNames.NameType?): String? { log("direct:$id:$type"); return name }
            override fun getExemplarLocationName(id: String?): String? { log("city:$id"); return if (id == "Etc/Unknown") unknownCity else city }
        }
        fun acquireNames(): SNames? { log("names:${nameQueries++}"); return if (nullNames) null else names }
        val zone = object : com.ibm.icu.util.TimeZone() {
            override fun getID(): String? { log("id:${this@Script.id}"); return this@Script.id }
            override fun getOffset(era: Int,year: Int,month: Int,day: Int,dayOfWeek: Int,millis: Int): Int { log("fieldOffset"); return raw+dst }
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray) { log("offset:$date:$local:${offsets.toList()}"); offsets[0] = raw; offsets[1] = dst; captured = offsets }
            override fun getRawOffset(): Int { log("raw"); return raw }
            override fun setRawOffset(value: Int) { raw = value }
            override fun useDaylightTime(): Boolean = daylight
            override fun inDaylightTime(date: java.util.Date?): Boolean { log("daylight:${date?.time}"); return daylight }
        }
        fun virtual(offset: Int,short: Boolean,normal: () -> String): String? {
            log("virtual:$short:$offset")
            return when(virtual) {
                0 -> normal()
                1 -> "virtual:$offset"
                2 -> null
                else -> { captured!![1] = if (dst == 0) 1 else 0; "mutated:$offset" }
            }
        }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is VirtualMachineError -> throw e; is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun virtualServiceAcquisitionFailureOrderingAndCapturedOffsets() {
        val script = Script()
        val a = object : Source(com.ibm.icu.util.ULocale.US) {
            override fun getTimeZoneNames(): SNames? = script.acquireNames()
            override fun formatOffsetLocalizedGMT(offset: Int): String? = script.virtual(offset,false) { super.formatOffsetLocalizedGMT(offset) }
            override fun formatOffsetShortLocalizedGMT(offset: Int): String? = script.virtual(offset,true) { super.formatOffsetShortLocalizedGMT(offset) }
        }
        val generic = object : SGeneric(com.ibm.icu.util.ULocale.US,script.names) {
            override fun getGenericLocationName(id: String?): String? { script.log("genericLocation:$id"); return script.generic }
            override fun getDisplayName(zone: com.ibm.icu.util.TimeZone?,type: SGeneric.GenericNameType?,date: Long): String? { script.log("genericDisplay:$type:$date"); return script.generic }
        }
        Source::class.java.getDeclaredField("_gnames").also { it.isAccessible = true }.set(a,generic)
        val owner = object : TimeZoneFormatDisplayCore.OwnerQueries<com.ibm.icu.util.TimeZone> {
            override fun getID(zone: com.ibm.icu.util.TimeZone?) = zone!!.id
            override fun getShortID(zone: com.ibm.icu.util.TimeZone?) = com.ibm.icu.impl.ZoneMeta.getShortID(zone)
            override fun getCanonicalCLDRID(zone: com.ibm.icu.util.TimeZone?) = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(zone)
            override fun inDaylightTime(zone: com.ibm.icu.util.TimeZone?,date: Long) = zone!!.inDaylightTime(java.util.Date(date))
            override fun getOffset(zone: com.ibm.icu.util.TimeZone?,date: Long,local: Boolean,offsets: IntArray) = zone!!.getOffset(date,local,offsets)
            override fun getNames(): TimeZoneFormatDisplayCore.Names? {
                val names = script.acquireNames() ?: return null
                return object : TimeZoneFormatDisplayCore.Names {
                    override fun getDisplayName(id: String?,type: NameType,date: Long) = names.getDisplayName(id,SNames.NameType.valueOf(type.name),date)
                    override fun getExemplarLocationName(id: String?) = names.getExemplarLocationName(id)
                }
            }
            override fun getGenericNames() = object : TimeZoneFormatDisplayCore.GenericNames<com.ibm.icu.util.TimeZone> {
                override fun getGenericLocationName(id: String?) = generic.getGenericLocationName(id)
                override fun getDisplayName(zone: com.ibm.icu.util.TimeZone?,type: GenericNameType,date: Long) = generic.getDisplayName(zone,SGeneric.GenericNameType.valueOf(type.name),date)
            }
            override fun formatLocalizedOffset(core: LocalizedGMTOffsetCore,offset: Int,short: Boolean) = script.virtual(offset,short) { if (short) core.formatOffsetShortLocalizedGMT(offset) else core.formatOffsetLocalizedGMT(offset) }
        }
        val b = TimeZoneFormatDisplayCore(LocalizedGMTOffsetCore(ULocale("en_US")),owner)
        fun compare(style: Source.Style?,zone: com.ibm.icu.util.TimeZone?,date: Long) {
            val at = com.ibm.icu.util.Output<Source.TimeType>(Source.TimeType.DAYLIGHT)
            val bt = Output(TimeType.DAYLIGHT)
            script.reset()
            val expected = outcome { a.format(style,zone,date,at) }
            val trace = script.trace.toList()
            script.reset()
            assertEquals(expected,outcome { b.format(style?.let { Style.valueOf(it.name) },zone,date,bt) },"$style/${script.id}/${script.virtual}/${script.fail}")
            assertEquals(at.value?.name,bt.value?.name,"timeType/$style")
            assertEquals(trace,script.trace,"trace/$style/${script.id}/${script.virtual}/${script.fail}")
        }
        for (mask in 0 until 32) {
            script.name = if (mask and 1 != 0) "name" else null
            script.metaName = if (mask and 2 != 0) "meta name" else null
            script.city = if (mask and 4 != 0) "city" else null
            script.unknownCity = if (mask and 8 != 0) "unknown" else null
            script.generic = if (mask and 16 != 0) "generic" else null
            script.daylight = mask and 1 != 0
            for ((raw,dst) in listOf(19_845_987 to 0,-19_845_987 to -3_600_000,Int.MAX_VALUE to Int.MAX_VALUE,86_400_000 to 0)) {
                script.raw = raw; script.dst = dst
                for (virtual in 0..3) {
                    script.virtual = virtual
                    for (style in Source.Style.entries) compare(style,script.zone,Long.MIN_VALUE)
                }
            }
        }
        script.name = null; script.metaName = null; script.generic = null; script.city = null; script.unknownCity = null
        script.raw = 19_800_000; script.dst = 0
        for (id in listOf("custom","Etc/Unknown","US/Pacific",null)) for (nullNames in listOf(false,true)) for (fail in listOf(null,"names","id","daylight","offset","direct","meta","metaName","city","genericLocation","genericDisplay","virtual")) {
            script.id = id; script.nullNames = nullNames; script.fail = fail
            for (style in Source.Style.entries+listOf(null)) {
                compare(style,script.zone,Long.MAX_VALUE)
                compare(style,null,Long.MAX_VALUE)
            }
        }
    }
}
