@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TimeZoneNamesTest {
    @Test fun factoriesNamesAndHistoricalMappings() {
        val names = TimeZoneNames.getInstance(ULocale("en_US"))!!
        assertSame(names, TimeZoneNames.getInstance(ULocale("en_US@calendar=hebrew")))
        assertNotSame(TimeZoneNames.getTZDBInstance(ULocale.US), TimeZoneNames.getTZDBInstance(ULocale.US))
        assertEquals("Eastern Standard Time", names.getDisplayName("America/New_York", TimeZoneNames.NameType.LONG_STANDARD, 0))
        assertEquals("America_Eastern", names.getMetaZoneID("America/New_York", 0))
        assertEquals("New York", names.getExemplarLocationName("America/New_York"))
        assertNull(names.getExemplarLocationName("Etc/GMT"))
        assertTrue(names.getAvailableMetaZoneIDs("America/Indiana/Knox").size > 1)
    }
    @Test fun matchesAndRegionalAbbreviations() {
        val names = TimeZoneNames.getInstance(ULocale.US)!!
        val match = names.find("xxEastern Standard Time", 2, setOf(TimeZoneNames.NameType.LONG_STANDARD))
        assertTrue(match!!.any { it.mzID() == "America_Eastern" && it.matchLength() == 21 })
        val tzdb = TimeZoneNames.getTZDBInstance(ULocale("en_IL"))
        val found = tzdb.find("IST", 0, setOf(TimeZoneNames.NameType.SHORT_STANDARD))
        assertEquals("Israel", found!!.single().mzID())
        assertFailsWith<IllegalArgumentException> { names.find("", 0, null) }
    }
    @Test fun matchValidationAndBulkDestination() {
        assertFailsWith<IllegalArgumentException> { TimeZoneNames.MatchInfo(null, "id", null, 1) }
        assertFailsWith<IllegalArgumentException> { TimeZoneNames.MatchInfo(TimeZoneNames.NameType.LONG_GENERIC, null, null, 1) }
        assertFailsWith<IllegalArgumentException> { TimeZoneNames.MatchInfo(TimeZoneNames.NameType.LONG_GENERIC, "id", null, 0) }
        val names = TimeZoneNames.getInstance(ULocale.US)!!
        val dest = arrayOf<String?>("left", null, null, "right")
        names.getDisplayNames("America/New_York", arrayOf(TimeZoneNames.NameType.LONG_STANDARD, TimeZoneNames.NameType.LONG_DAYLIGHT), 0, dest, 1)
        assertContentEquals(arrayOf("left", "Eastern Standard Time", "Eastern Daylight Time", "right"), dest)
        names.getDisplayNames(null, null, 0, null, Int.MIN_VALUE)
    }
}
