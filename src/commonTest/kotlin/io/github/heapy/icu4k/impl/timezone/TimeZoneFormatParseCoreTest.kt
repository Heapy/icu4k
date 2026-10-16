package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneFormatParseCoreTest {
    private class Owner : OwnerQueries<String> {
        val trace = mutableListOf<String>()
        var specific: Collection<MatchInfo>? = emptyList()
        var tzdb: Collection<MatchInfo>? = emptyList()
        var generic: GenericMatchInfo? = null
        var options = emptySet<ParseOption>()
        override fun findNames(text: String,start: Int,types: Set<NameType>): Collection<MatchInfo>? { trace.add("names"); return specific }
        override fun findTZDBNames(text: String,start: Int,types: Set<NameType>): Collection<MatchInfo>? { trace.add("tzdb"); return tzdb }
        override fun findBestGenericMatch(text: String,start: Int,types: Set<GenericNameType>): GenericMatchInfo? { trace.add("generic"); return generic }
        override fun getReferenceZoneID(metaZone: String?,region: String): String? { trace.add("region:$region"); return if (metaZone == "America_Central") "America/Chicago" else null }
        override fun getTimeZone(id: String?): String = id!!
        override fun getCustomTimeZone(offset: Int): String = "offset:$offset"
        override fun getDefaultParseOptions(): Set<ParseOption> = options
    }
    private fun core(owner: Owner) = TimeZoneFormatParseCore(ULocale("en"),LocalizedGMTOffsetCore(ULocale("en")),owner)
    @Test fun numericFallbackAndLocalIsoUtcBehavior() {
        val owner = Owner()
        val core = core(owner)
        val output = Output(TimeType.DAYLIGHT)
        val pos = ParsePosition(0).also { it.errorIndex = 17 }
        assertEquals("offset:19800000",core.parse(Style.SPECIFIC_LONG,"GMT+05:30",pos,null,output))
        assertEquals(emptyList(),owner.trace)
        assertEquals(9,pos.index)
        assertEquals(17,pos.errorIndex)
        assertEquals(TimeType.UNKNOWN,output.value)
        assertEquals("Etc/GMT",core.parse(Style.ISO_BASIC_LOCAL_FIXED,"Z",ParsePosition(0),null,output))
        assertNull(core.parse(Style.ZONE_ID,"missing",ParsePosition(0),emptySet(),output))
    }
    @Test fun namePrecedenceMetazoneAndLongestMatch() {
        val owner = Owner()
        val core = core(owner)
        owner.specific = listOf(MatchInfo(NameType.SHORT_STANDARD,null,"America_Central",3))
        owner.tzdb = listOf(MatchInfo(NameType.SHORT_DAYLIGHT,"Europe/London",null,5))
        owner.generic = GenericMatchInfo("Asia/Kolkata",7,TimeType.DAYLIGHT)
        val output = Output<TimeType>()
        assertEquals("America/Chicago",core.parse(Style.SPECIFIC_SHORT,"CST suffix",ParsePosition(0),ParseOption.entries.toSet(),output))
        assertEquals(listOf("names","region:US"),owner.trace)
        assertEquals(TimeType.STANDARD,output.value)
        owner.trace.clear()
        assertEquals("Asia/Kolkata",core.parse(Style.ZONE_ID,"abcdefghi suffix",ParsePosition(0),ParseOption.entries.toSet(),output))
        assertEquals(listOf("names","region:US","tzdb","generic"),owner.trace)
        assertEquals(TimeType.DAYLIGHT,output.value)
        owner.specific = listOf(MatchInfo(NameType.EXEMPLAR_LOCATION,"Europe/Paris",null,5),MatchInfo(NameType.EXEMPLAR_LOCATION,"Europe/London",null,5))
        val pos = ParsePosition(0)
        assertEquals("Europe/Paris",core.parseExemplarLocation("Paris suffix",pos))
        assertEquals(5,pos.index)
    }
    @Test fun idTriesPrefixesAndInvalidRanges() {
        val pos = ParsePosition(1).also { it.errorIndex = 23 }
        assertEquals("America/New_York",TimeZoneIDParseCore.parseZoneID("!america/new_york suffix",pos))
        assertEquals(17,pos.index)
        assertEquals(23,pos.errorIndex)
        assertEquals("America/Los_Angeles",TimeZoneIDParseCore.parseShortZoneID("USLAX_more",ParsePosition(0)))
        assertEquals("Etc/Unknown",TimeZoneIDParseCore.parseShortZoneID("unk",ParsePosition(0)))
        assertNull(TimeZoneIDParseCore.parseZoneID("Etc/Unknown",ParsePosition(0)))
        for (index in listOf(Int.MIN_VALUE,-1,Int.MAX_VALUE)) assertFailsWith<IndexOutOfBoundsException> { TimeZoneIDParseCore.parseZoneID("UTC",ParsePosition(index)) }
    }
}
