@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.impl.TimeZoneGenericNames as SGeneric
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneFormatDisplayCoreOracleTest {
    private val genericGetter = Source::class.java.getDeclaredMethod("getTimeZoneGenericNames").also { it.isAccessible = true }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is VirtualMachineError -> throw e; is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private class Pairing(val a: Source,val b: TimeZoneFormatDisplayCore<com.ibm.icu.util.TimeZone>)
    private fun pair(locale: String): Pairing {
        val a = object : Source(com.ibm.icu.util.ULocale(locale)) { }
        val names = TimeZoneNamesDataCore(ULocale(locale),{ com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(com.ibm.icu.util.TimeZone.getDefault()) })
        val nameService = object : TimeZoneFormatDisplayCore.Names {
            override fun getDisplayName(id: String?,type: NameType,date: Long) = names.getDisplayName(id,type,date)
            override fun getExemplarLocationName(id: String?) = names.getExemplarLocationName(id)
        }
        val owner = object : TimeZoneFormatDisplayCore.OwnerQueries<com.ibm.icu.util.TimeZone> {
            override fun getID(zone: com.ibm.icu.util.TimeZone?) = zone!!.id
            override fun getShortID(zone: com.ibm.icu.util.TimeZone?) = com.ibm.icu.impl.ZoneMeta.getShortID(zone)
            override fun getCanonicalCLDRID(zone: com.ibm.icu.util.TimeZone?) = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(zone)
            override fun inDaylightTime(zone: com.ibm.icu.util.TimeZone?,date: Long) = zone!!.inDaylightTime(java.util.Date(date))
            override fun getOffset(zone: com.ibm.icu.util.TimeZone?,date: Long,local: Boolean,offsets: IntArray) = zone!!.getOffset(date,local,offsets)
            override fun getNames() = nameService
            override fun getGenericNames(): TimeZoneFormatDisplayCore.GenericNames<com.ibm.icu.util.TimeZone> {
                val service = genericGetter.invoke(a) as SGeneric
                return object : TimeZoneFormatDisplayCore.GenericNames<com.ibm.icu.util.TimeZone> {
                    override fun getGenericLocationName(id: String?) = service.getGenericLocationName(id)
                    override fun getDisplayName(zone: com.ibm.icu.util.TimeZone?,type: GenericNameType,date: Long) = service.getDisplayName(zone,SGeneric.GenericNameType.valueOf(type.name),date)
                }
            }
        }
        return Pairing(a,TimeZoneFormatDisplayCore(LocalizedGMTOffsetCore(ULocale(locale)),owner))
    }
    private fun compare(pair: Pairing,style: Source.Style?,zone: com.ibm.icu.util.TimeZone?,date: Long) {
        val a = com.ibm.icu.util.Output<Source.TimeType>(Source.TimeType.DAYLIGHT)
        val b = Output(TimeType.DAYLIGHT)
        assertEquals(outcome { pair.a.format(style,zone,date,a) },outcome { pair.b.format(style?.let { Style.valueOf(it.name) },zone,date,b) },"$style/${zone?.id}/$date")
        assertEquals(a.value?.name,b.value?.name,"timeType/$style/${zone?.id}/$date")
    }
    @Test fun allZonesStylesSeasonsAndLocales() {
        val ids = com.ibm.icu.util.TimeZone.getAvailableIDs().toList()+listOf("Etc/Unknown","GMT+05:30:45","GMT-00:00:01","unknown")
        for (locale in listOf("root","en_US","en_GB","fr","ar","zh","he")) {
            val pair = pair(locale)
            for (id in ids) {
                val zone = com.ibm.icu.util.TimeZone.getTimeZone(id)
                for (date in listOf(0L,1_704_067_200_000L,1_719_792_000_000L)) for (style in Source.Style.entries) compare(pair,style,zone,date)
            }
        }
    }
    @Test fun unknownAndCustomOffsetBoundariesNullAndExtremeDates() {
        val pair = pair("en_US")
        val zones = listOf(null,com.ibm.icu.util.TimeZone.getTimeZone("Europe/London"),com.ibm.icu.util.TimeZone.getTimeZone("Etc/Unknown"))+
            listOf(Int.MIN_VALUE,Int.MAX_VALUE,-86_400_000,-86_399_999,-19_845_987,-999,0,999,19_845_987,86_399_999,86_400_000).map { com.ibm.icu.util.SimpleTimeZone(it,"custom") }
        for (zone in zones) for (date in listOf(Long.MIN_VALUE,-2_208_988_800_000L,0L,Long.MAX_VALUE)) for (style in Source.Style.entries+listOf(null)) compare(pair,style,zone,date)
        for (style in Source.Style.entries) {
            val zone = com.ibm.icu.util.TimeZone.getTimeZone("America/New_York")
            assertEquals(pair.a.format(style,zone,0,null),pair.b.format(Style.valueOf(style.name),zone,0,null))
        }
    }
}
