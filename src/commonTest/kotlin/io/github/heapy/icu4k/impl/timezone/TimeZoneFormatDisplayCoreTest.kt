package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneFormatDisplayCoreTest {
    private class Owner : TimeZoneFormatDisplayCore.OwnerQueries<String> {
        val trace = mutableListOf<String>()
        var name: String? = null
        var city: String? = null
        var unknownCity: String? = null
        var generic: String? = null
        var shortID: String? = null
        var raw = 19_800_000
        var dst = 0
        var daylight = false
        var capture: IntArray? = null
        var changeDstDuringFormat = false
        override fun getID(zone: String?): String? { trace.add("id"); return zone }
        override fun getShortID(zone: String?): String? { trace.add("shortID"); return shortID }
        override fun getCanonicalCLDRID(zone: String?): String? { trace.add("canonical"); return zone }
        override fun inDaylightTime(zone: String?,date: Long): Boolean { trace.add("daylight"); return daylight }
        override fun getOffset(zone: String?,date: Long,local: Boolean,offsets: IntArray) {
            trace.add("offset:$local"); offsets[0] = raw; offsets[1] = dst; capture = offsets
        }
        override fun getNames(): TimeZoneFormatDisplayCore.Names {
            trace.add("names")
            return object : TimeZoneFormatDisplayCore.Names {
                override fun getDisplayName(id: String?,type: NameType,date: Long): String? { trace.add("display:$type"); return name }
                override fun getExemplarLocationName(id: String?): String? { trace.add("city:$id"); return if (id == "Etc/Unknown") unknownCity else city }
            }
        }
        override fun getGenericNames(): TimeZoneFormatDisplayCore.GenericNames<String> {
            trace.add("generic")
            return object : TimeZoneFormatDisplayCore.GenericNames<String> {
                override fun getGenericLocationName(id: String?): String? { trace.add("location"); return generic }
                override fun getDisplayName(zone: String?,type: GenericNameType,date: Long): String? { trace.add("genericDisplay:$type"); return generic }
            }
        }
        override fun formatLocalizedOffset(core: LocalizedGMTOffsetCore,offset: Int,short: Boolean): String {
            trace.add("format:$short")
            if (changeDstDuringFormat) capture!![1] = 1
            return if (short) core.formatOffsetShortLocalizedGMT(offset) else core.formatOffsetLocalizedGMT(offset)
        }
    }
    @Test fun specificNamesAndOffsetFallback() {
        val owner = Owner()
        val core = TimeZoneFormatDisplayCore(LocalizedGMTOffsetCore(ULocale("en")),owner)
        val output = Output<TimeType>()
        assertEquals("GMT+05:30",core.format(Style.SPECIFIC_LONG,"zone",0,output))
        assertEquals(listOf("daylight","names","canonical","display:LONG_STANDARD","offset:false","format:false"),owner.trace)
        assertEquals(TimeType.STANDARD,output.value)
        owner.trace.clear(); owner.daylight = true; owner.name = "summer name"
        assertEquals("summer name",core.format(Style.SPECIFIC_SHORT,"zone",0,output))
        assertEquals(listOf("daylight","names","canonical","display:SHORT_DAYLIGHT"),owner.trace)
        assertEquals(TimeType.DAYLIGHT,output.value)
        owner.name = null; owner.changeDstDuringFormat = true
        assertEquals("GMT+05:30",core.format(Style.LOCALIZED_GMT,"zone",0,output))
        assertEquals(TimeType.DAYLIGHT,output.value)
    }
    @Test fun genericExemplarAndUnknownShortId() {
        val owner = Owner()
        val core = TimeZoneFormatDisplayCore(LocalizedGMTOffsetCore(ULocale("en")),owner)
        val output = Output(TimeType.DAYLIGHT)
        owner.generic = "generic name"
        assertEquals("generic name",core.format(Style.GENERIC_LOCATION,"zone",0,output))
        assertEquals(listOf("generic","canonical","location"),owner.trace)
        assertEquals(TimeType.UNKNOWN,output.value)
        owner.trace.clear()
        assertEquals("Unknown",core.format(Style.EXEMPLAR_LOCATION,"zone",0,output))
        assertEquals(listOf("names","canonical","city:zone","names","city:Etc/Unknown"),owner.trace)
        assertEquals("unk",core.format(Style.ZONE_ID_SHORT,"zone",0,output))
        assertEquals(TimeType.UNKNOWN,output.value)
        owner.unknownCity = "unknown city"
        assertEquals("unknown city",core.format(Style.EXEMPLAR_LOCATION,"zone",0,output))
    }
    @Test fun allIsoFlagsAndIntOverflow() {
        val owner = Owner()
        val core = TimeZoneFormatDisplayCore(LocalizedGMTOffsetCore(ULocale("en")),owner)
        owner.raw = -19_845_987
        val expected = mapOf(Style.ISO_BASIC_SHORT to "-0530",Style.ISO_BASIC_FIXED to "-0530",Style.ISO_BASIC_FULL to "-053045",Style.ISO_EXTENDED_FIXED to "-05:30",Style.ISO_EXTENDED_FULL to "-05:30:45")
        for ((style,text) in expected) assertEquals(text,core.format(style,"zone",0))
        owner.raw = Int.MAX_VALUE; owner.dst = Int.MAX_VALUE
        assertEquals("Z",core.format(Style.ISO_BASIC_FULL,"zone",0))
        val output = Output(TimeType.DAYLIGHT)
        assertFailsWith<NullPointerException> { core.format(null,null,0,output) }
        assertEquals(TimeType.UNKNOWN,output.value)
    }
}
