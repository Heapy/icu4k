package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.TimeZoneNames
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateFormatSymbolsZoneStringsCoreOracleTest {
    @Test fun commonNamesMatchEverySourceZoneIncludingAliases() {
        val ids = TimeZone.getAvailableIDs()
        val types = arrayOf(TimeZoneNames.NameType.LONG_STANDARD, TimeZoneNames.NameType.SHORT_STANDARD,
            TimeZoneNames.NameType.LONG_DAYLIGHT, TimeZoneNames.NameType.SHORT_DAYLIGHT)
        var now = 0L
        var defaults = 0
        val core = DateFormatSymbolsZoneStringsCore.withCommonNames({ now }, {
            defaults++
            com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(TimeZone.getDefault())
        })
        for (locale in listOf("en_US", "fr", "ru", "zh_Hant", "ar", "en_US@hours=h23")) {
            val source = TimeZoneNames.getInstance(com.ibm.icu.util.ULocale(locale))
            source.loadAllDisplayNames()
            for (date in listOf(0L, 946684800000L, 1792195200000L)) {
                now = date
                val data = DateFormatSymbolsDataCore(object : DateFormatSymbolsDataCore.Owner {
                    override fun className() = "test.Symbols"
                    override fun currentTimeMillis() = error("unused era clock")
                    override fun defaultZoneOffset(time: Long): Int = error("unused era zone")
                })
                data.validLocale = ULocale(locale)
                val actual = core.getZoneStrings(DateFormatSymbolsStateCore(data))
                assertEquals(ids.size, actual.size)
                for (i in ids.indices) {
                    val expected = arrayOfNulls<String>(5)
                    expected[0] = ids[i]
                    source.getDisplayNames(TimeZone.getCanonicalID(ids[i]) ?: ids[i], types, date, expected, 1)
                    assertContentEquals(expected, actual[i], "$locale/$date/${ids[i]}")
                }
            }
        }
        // Soft cache entries may be reclaimed between calls.
        assertTrue(defaults >= 5)
    }

    @Test fun initialReturnAndLaterCopiesMatchSource() {
        val source = com.ibm.icu.text.DateFormatSymbols(com.ibm.icu.util.ULocale.US)
        val first = source.zoneStrings
        first[0][1] = "changed"
        val second = source.zoneStrings
        assertNotSame(first, second); assertNotSame(first[0], second[0])
        assertEquals("changed", second[0][1])
        second[0][1] = "copy"
        assertEquals("changed", source.zoneStrings[0][1])
    }
}
