package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType.*

class TimeZoneNamesSearchCoreTest {
    private fun core(locale: String = "en") = TimeZoneNamesDataCore(ULocale(locale),{ "Etc/UTC" })
    @Test fun cachedAndFullyLoadedSearchFindCaseFoldedPrefixes() {
        val names = core()
        names.getExemplarLocationName("America/Los_Angeles")
        val cached = names.find("lOs AnGeLeS",0,setOf(EXEMPLAR_LOCATION))
        assertTrue(cached.any { it.tzID() == "America/Los_Angeles" && it.matchLength() == 11 })
        val full = names.find("xxPacific Standard Time trailing",2,setOf(LONG_STANDARD))
        assertTrue(full.any { it.mzID() == "America_Pacific" && it.matchLength() == 21 })
        assertTrue(full.all { it.nameType() == LONG_STANDARD })
        assertTrue(names.find("nothing matching 0123456789",0,null).isEmpty())
    }
    @Test fun canonicalDefaultCitiesAndLocalizedNamesEnterTheTrie() {
        val names = core("ru")
        val city = assertNotNull(names.getExemplarLocationName("America/New_York"))
        assertTrue(names.find(city,0,setOf(EXEMPLAR_LOCATION)).any { it.tzID() == "America/New_York" })
        val en = core()
        en.loadAllDisplayNames()
        assertTrue(en.find("Los Angeles suffix",0,null).any { it.tzID() == "America/Los_Angeles" })
        assertTrue(en.find("Pacific Standard Time",0,setOf(LONG_DAYLIGHT)).isEmpty())
    }
    @Test fun matchCollectionsAreIndependentAndEmptyResultsRejectInsertion() {
        val names = core()
        val matches = names.find("Pacific Standard Time",0,null) as MutableCollection<TimeZoneNamesDataCore.MatchInfo>
        assertTrue(matches.isNotEmpty())
        matches.clear()
        assertTrue(names.find("Pacific Standard Time",0,null).isNotEmpty())
        val empty = names.find("Pacific Standard Time",0,emptySet()) as MutableCollection<TimeZoneNamesDataCore.MatchInfo>
        empty.clear()
        assertFalse(empty.remove(TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,null,"America_Pacific",1)))
        assertFailsWith<UnsupportedOperationException> { empty.add(TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,null,"America_Pacific",1)) }
    }
    @Test fun validationRetainsMatchInfoAndTextFailureOrder() {
        val names = core()
        for (text in listOf(null,"")) assertFailsWith<IllegalArgumentException> { names.find(text,0,null) }
        for (start in listOf(-1,3,Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { names.find("abc",start,null) }
        assertEquals("nameType is null",assertFailsWith<IllegalArgumentException> { TimeZoneNamesDataCore.MatchInfo(null,null,null,0) }.message)
        assertEquals("Either tzID or mzID must be available",assertFailsWith<IllegalArgumentException> { TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,null,null,0) }.message)
        assertEquals("matchLength must be positive value",assertFailsWith<IllegalArgumentException> { TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,"",null,0) }.message)
        val both = TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,"zone","meta",1)
        assertEquals("zone",both.tzID()); assertEquals("meta",both.mzID())
        assertEquals(LONG_STANDARD,both.nameType()); assertEquals(1,both.matchLength())
        assertNotEquals(both,TimeZoneNamesDataCore.MatchInfo(LONG_STANDARD,"zone","meta",1))
    }

    @Test fun textReadsCanMutateFiltersAndReenterNameLoading() {
        val names = core()
        val types = mutableSetOf(LONG_STANDARD)
        val value = "Pacific Standard Time"
        var called = false
        val text = object : CharSequence {
            override val length: Int get() = value.length
            override fun get(index: Int): Char {
                if(!called) { called = true; types.clear(); names.getExemplarLocationName("Area/Callback_City") }
                return value[index]
            }
            override fun subSequence(startIndex: Int,endIndex: Int): CharSequence = value.subSequence(startIndex,endIndex)
        }
        assertTrue(names.find(text,0,types).isEmpty())
        assertTrue(called)
        assertTrue(names.find("Callback City",0,setOf(EXEMPLAR_LOCATION)).any { it.tzID() == "Area/Callback_City" })
    }

    @Test fun supplementaryCaseFoldedCityNamesReportUtf16Length() {
        val names = core()
        val upper = "\uD801\uDC00"
        val lower = "\uD801\uDC28"
        val id = "Area/$upper"
        assertEquals(upper,names.getExemplarLocationName(id))
        val matches = names.find("xx$lower",2,setOf(EXEMPLAR_LOCATION))
        val match = matches.first { it.tzID() == id }
        assertEquals(2,match.matchLength())
    }
}
