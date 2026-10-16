package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternHourDataCoreTest {
    @Test fun resourcePreferredHoursAndLocaleOverrides() {
        val data=DateTimePatternHourDataCore(ICUResourceBundleReader.embeddedLoader)
        for((id,hour) in listOf("en_US" to 'h',"en_GB" to 'H',"fr_FR" to 'H',"en_US@rg=gbzzzz" to 'H',"en_US@hours=h11" to 'K',"en_GB@hours=h24" to 'k')) {
            assertEquals(hour,data.get(ULocale(id)).preferred,id)
        }
        val original=data.get(ULocale("en_US"))
        assertContentEquals(original.allowed,data.get(ULocale("en_US@hours=h23")).allowed)
        original.allowed[0]="mutated"
        assertFalse(data.get(ULocale("en_US")).allowed[0]=="mutated")
    }
    @Test fun hourDataFeedsCommonSkeletonMetacharacters() {
        val data=DateTimePatternHourDataCore(ICUResourceBundleReader.embeddedLoader)
        val core=DateTimePatternGeneratorCore.empty()
        core.initializeHourFormats(ULocale("en_US"),data)
        assertEquals(DateTimePatternGeneratorCore.HourCycle.HOUR_CYCLE_12,core.getDefaultHourCycle())
        assertTrue(core.getBestPattern("Cmm").isNotEmpty())
        core.initializeHourFormats(ULocale("en_US@hours=h23"),data)
        assertEquals(DateTimePatternGeneratorCore.HourCycle.HOUR_CYCLE_23,core.getDefaultHourCycle())
        assertTrue(core.getBestPattern("jmm").isNotEmpty())
    }
}
