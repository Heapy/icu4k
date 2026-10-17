package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.text.DateTimePatternGenerator

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.test.*

class DateTimePatternResourceCoreTest {
    @Test fun embeddedLocaleNamesAndAvailablePatternsRespectExistingValues() {
        val data=DateTimePatternResourceCore(ICUResourceBundleReader.embeddedLoader)
        val core=DateTimePatternGeneratorCore(); core.addCanonicalItems()
        val info=DateTimePatternGenerator.PatternInfo()
        data.addStandardPatterns(core,ULocale.US,info)
        data.addCLDRData(core,ULocale.US,info)
        assertEquals("month",core.getAppendItemName(3))
        assertTrue(core.isAvailableFormatSet("yMd"))
        assertEquals("M/d/y",core.getSkeletons(null)["yMd"])
        data.addCLDRData(core,ULocale.FRANCE,info)
        assertEquals("month",core.getAppendItemName(3))
        assertEquals("M/d/y",core.getSkeletons(null)["yMd"])
    }
    @Test fun calendarSelectionAndMissingStandardPatterns() {
        val data=DateTimePatternResourceCore(ICUResourceBundleReader.embeddedLoader)
        assertEquals("persian",data.calendarType(ULocale("fa_IR")))
        assertEquals("UNKNOWN",data.calendarType(ULocale("en_US@calendar=UNKNOWN")))
        val core=DateTimePatternGeneratorCore(); val info=DateTimePatternGenerator.PatternInfo()
        assertFailsWith<MissingResourceException> { data.addStandardPatterns(core,ULocale("en_US@calendar=UNKNOWN"),info) }
        data.addCLDRData(core,ULocale("en_US@calendar=UNKNOWN"),info)
        assertEquals("month",core.getAppendItemName(3))
        assertTrue(core.getSkeletons(null).isEmpty())
        assertEquals(5,DateTimePatternResourceCore.appendFormatNumber("*"))
    }
}
