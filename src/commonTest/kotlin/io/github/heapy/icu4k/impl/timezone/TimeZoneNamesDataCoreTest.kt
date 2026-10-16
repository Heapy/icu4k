package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType.*

class TimeZoneNamesDataCoreTest {
    private fun names(locale: String) = TimeZoneNamesDataCore(ULocale(locale),{ "Etc/UTC" })
    @Test fun localizedNamesFallbackAndMetazonesUseCommonResources() {
        val en = names("en_US")
        assertEquals("Pacific Standard Time",en.getDisplayName("America/Los_Angeles",LONG_STANDARD,0))
        assertEquals("Pacific Daylight Time",en.getDisplayName("America/Los_Angeles",LONG_DAYLIGHT,0))
        assertEquals("Los Angeles",en.getExemplarLocationName("America/Los_Angeles"))
        assertEquals("America_Pacific",en.getMetaZoneID("America/Los_Angeles",0))
        assertNull(en.getMetaZoneID("America/Los_Angeles",-1))
        assertNull(en.getMetaZoneID("US/Pacific",0))
        assertEquals("America/Los_Angeles",en.getReferenceZoneID("America_Pacific",null))
        assertEquals("America/Vancouver",en.getReferenceZoneID("America_Pacific","CA"))
        val fr = names("fr_CA")
        assertEquals("Los Angeles",fr.getExemplarLocationName("America/Los_Angeles"))
        assertNotEquals(en.getDisplayName("America/Los_Angeles",LONG_STANDARD,0),fr.getDisplayName("America/Los_Angeles",LONG_STANDARD,0))
        val ru = names("ru")
        assertEquals("Нью-Йорк",ru.getExemplarLocationName("America/New_York"))
        assertNull(en.getTimeZoneDisplayName(null,null))
        assertFailsWith<NullPointerException> { en.getTimeZoneDisplayName("Unknown/Zone",null) }
    }
    @Test fun bulkLoadingPreservesAlreadyLoadedAndFallbackNames() {
        for(locale in listOf("en","en_GB","fr","ru","zh_Hant","ar")) {
            val data = names(locale)
            val before = TimeZoneNamesDataCore.NameType.entries.map { data.getDisplayName("America/Los_Angeles",it,0) }
            data.loadAllDisplayNames(); data.loadAllDisplayNames()
            assertEquals(before,TimeZoneNamesDataCore.NameType.entries.map { data.getDisplayName("America/Los_Angeles",it,0) },locale)
        }
        assertTrue(ICUData.exists("zone/pool.res")); assertTrue(ICUData.exists("zone/res_index.res"))
        assertNotNull(ICUData.getBytes("zone/en.res"))
    }
    @Test fun batchNamesRetainLazyMetazoneLookupAndPartialWrites() {
        val data = names("en")
        val types = arrayOf(LONG_STANDARD,LONG_DAYLIGHT,EXEMPLAR_LOCATION)
        val dest = arrayOf<String?>("before",null,null,null,"after")
        var calls = 0
        data.getDisplayNames("America/Los_Angeles",types,0,dest,1) { _,_ -> ++calls; "America_Pacific" }
        assertContentEquals(arrayOf("before","Pacific Standard Time","Pacific Daylight Time","Los Angeles","after"),dest)
        assertEquals(1,calls)
        val short = arrayOf<String?>("unchanged")
        assertFailsWith<IndexOutOfBoundsException> { data.getDisplayNames("America/Los_Angeles",types,0,short,0) }
        assertEquals("Pacific Standard Time",short[0])
        data.getDisplayNames(null,null,0,null,Int.MIN_VALUE)
        data.getDisplayNames("America/Los_Angeles",emptyArray(),0,null,Int.MIN_VALUE)
        assertFailsWith<NullPointerException> { data.getDisplayNames("America/Los_Angeles",null,0,null,0) }
    }
    @Test fun defaultLocationExclusionsMatchJavaLineRules() {
        fun location(id: String?) = TimeZoneNamesDataCore.getDefaultExemplarLocationName(id)
        assertEquals("New York",location("America/New_York"))
        assertNull(location("Etc/UTC")); assertNull(location("SystemV/EST5")); assertNull(location("Mideast/Riyadh87"))
        assertNull(location("/City")); assertNull(location("Area/")); assertNull(location(null))
        assertEquals("UTC\u0085",location("Etc/UTC\u0085"))
        assertEquals("City",location("Area\n/City"))
    }
    @Test fun mappingSetsDatesAndHalfOpenIntervalsRetainSourceRules() {
        val all = TimeZoneNamesMappings.getAvailableMetaZoneIDs()
        assertSame(all,TimeZoneNamesMappings.getAvailableMetaZoneIDs())
        assertTrue("America_Pacific" in all)
        @Suppress("UNCHECKED_CAST")
        val nullableAll = all as Set<String?>
        assertFailsWith<NullPointerException> { nullableAll.contains(null) }
        val ids = TimeZoneNamesMappings.getAvailableMetaZoneIDs("America/Los_Angeles")
        assertTrue("America_Pacific" in ids)
        @Suppress("UNCHECKED_CAST")
        val nullableIDs = ids as Set<String?>
        assertFalse(nullableIDs.contains(null))
        assertFailsWith<UnsupportedOperationException> { (ids as MutableSet<String>).clear() }
        val empty = TimeZoneNamesMappings.getAvailableMetaZoneIDs(null) as MutableSet<String>
        empty.clear(); assertFalse(empty.remove("missing"))
        assertEquals(0,TimeZoneNamesMappings.parseDate("1970x01x01x00x00 extra"))
        assertFailsWith<IllegalArgumentException> { TimeZoneNamesMappings.parseDate("x970-01-01 00:00") }
        assertFailsWith<IndexOutOfBoundsException> { TimeZoneNamesMappings.parseDate("1970") }
        assertFailsWith<NullPointerException> { TimeZoneNamesMappings.parseDate(null) }
    }
}
