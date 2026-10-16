package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneGenericNamesCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.text.SimpleFormatter
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneGenericNamesCoreTest {
    private fun core(): TimeZoneGenericNamesCore<OlsonTimeZoneCore> {
        val data = TimeZoneNamesDataCore(ULocale("en"),{ "America/New_York" })
        val names = object : Names {
            override fun getAvailableMetaZoneIDs(id: String) = data.getAvailableMetaZoneIDs(id)
            override fun getMetaZoneID(id: String,date: Long) = data.getMetaZoneID(id,date)
            override fun getReferenceZoneID(id: String?,region: String) = data.getReferenceZoneID(id,region)
            override fun getMetaZoneDisplayName(id: String,type: NameType) = data.getMetaZoneDisplayName(id,type)
            override fun getTimeZoneDisplayName(id: String,type: NameType) = data.getTimeZoneDisplayName(id,type)
            override fun getDisplayName(id: String,type: NameType,date: Long) = data.getDisplayName(id,type,date)
            override fun getExemplarLocationName(id: String) = data.getExemplarLocationName(id)
            override fun find(text: String,start: Int,types: Set<NameType>) = data.find(text,start,types)
        }
        val owner = object : OwnerQueries<OlsonTimeZoneCore> {
            override fun getDefaultNames(locale: ULocale?) = names
            override fun getDefaultTimeZone() = ZoneMetaCore.getSystemTimeZoneCore("America/New_York")
            override fun getCanonicalCLDRID(zone: OlsonTimeZoneCore?) = ZoneMetaCore.getCanonicalCLDRID(zone!!.getID())
            override fun getOffset(zone: OlsonTimeZoneCore?,date: Long,local: Boolean,offsets: IntArray) = zone!!.getOffset(date,local,offsets)
            override fun getBasicTimeZone(zone: OlsonTimeZoneCore?) = zone!!.basicCore()
            override fun getFrozenTimeZone(id: String) = ZoneMetaCore.getSystemTimeZoneCore(id)
            override fun createPatternFormatter(pattern: String?): PatternFormatter {
                // This fixture uses only the known simple en patterns, not a production MessageFormat substitute.
                val formatter = SimpleFormatter.compile(pattern)
                return PatternFormatter { formatter.format(*it) }
            }
        }
        return TimeZoneGenericNamesCore(ULocale("en"),names,owner)
    }
    @Test fun commonNamesOffsetsAndLazyParsing() {
        val core = core()
        assertEquals("Los Angeles Time",core.getGenericLocationName("America/Los_Angeles"))
        assertEquals("United Kingdom Time",core.getGenericLocationName("Europe/London"))
        assertNull(core.getGenericLocationName("Etc/UTC"))
        val zone = assertNotNull(ZoneMetaCore.getSystemTimeZoneCore("America/Los_Angeles"))
        assertEquals("Pacific Time",core.getDisplayName(zone,GenericNameType.LONG,1_719_792_000_000L))
        val match = assertNotNull(core.findBestMatch("!los angeles time suffix",1,setOf(GenericNameType.LOCATION)))
        assertEquals("America/Los_Angeles",match.tzID())
        assertEquals(16,match.matchLength())
        assertEquals(GenericNameType.LOCATION,match.nameType())
        val matches = assertNotNull(core.find("Pacific Time",0,setOf(GenericNameType.LONG)))
        assertTrue(matches.isNotEmpty())
        assertNull(core.find("definitely missing",0,emptySet()))
    }
    @Test fun sourcePatternInvalidationAndSharedCloneArray() {
        val core = core()
        assertEquals("Los Angeles Time",core.getGenericLocationName("America/Los_Angeles"))
        val copy = core.freeze().cloneAsThawed()
        assertFalse(copy.isFrozen())
        assertFailsWith<UnsupportedOperationException> { core.setFormatPattern(null,null) }
        copy.setFormatPattern(Pattern.REGION_FORMAT,"X{0}")
        assertFailsWith<NullPointerException> { copy.getGenericLocationName("Europe/London") }
        assertEquals("XUnited Kingdom",copy.getGenericLocationName("Europe/London"))
        assertEquals("XUnited Kingdom",core.getGenericLocationName("Europe/London"))
        assertEquals("Los Angeles Time",core.getGenericLocationName("America/Los_Angeles"))
        assertFailsWith<NullPointerException> { copy.find("test",0,setOf(GenericNameType.LOCATION)) }
    }
    @Test fun validationNullFiltersAndFallbackTypes() {
        val core = core()
        for ((text,start) in listOf(null to 0,"" to 0,"x" to -1,"x" to 1)) {
            assertFailsWith<IllegalArgumentException> { core.find(text,start,null) }
            assertFailsWith<IllegalArgumentException> { core.findBestMatch(text,start,null) }
        }
        assertFailsWith<NullPointerException> { core.findBestMatch("Pacific Time",0,null) }
        assertTrue(GenericNameType.LOCATION.isFallbackTypeOf(GenericNameType.LONG))
        assertFalse(GenericNameType.LONG.isFallbackTypeOf(GenericNameType.LOCATION))
        assertFailsWith<NullPointerException> { GenericNameType.LONG.isFallbackTypeOf(null) }
    }
}
