package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.SimpleCache
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternInitializationCoreTest {
    @Test fun noStandardInitializationUsesLocaleDataAndIsUncachedAndThawed() {
        val init=DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader)
        val us=init.noStandardPatterns(ULocale.US)
        assertEquals("M/d/y",us.getBestPattern("yMd"))
        assertEquals("h:mm\u202fa",us.getBestPattern("jmm"))
        assertEquals(".",us.getDecimal()); assertFalse(us.isFrozen())
        for(style in 0..3) assertEquals("{1} {0}",us.getDateTimeFormat(style))
        val french=init.noStandardPatterns(ULocale.FRANCE)
        assertEquals(",",french.getDecimal()); assertEquals("HH:mm",french.getBestPattern("jmm"))
        assertNotSame(us,init.noStandardPatterns(ULocale.US))
        us.setDecimal("changed"); assertEquals(".",init.noStandardPatterns(ULocale.US).getDecimal())
    }
    @Test fun calendarResourcePipelineUsesActualCommonNoStandardGenerator() {
        val patterns=CalendarPatternDataCore.withCommonGenerator(ICUResourceBundleReader.embeddedLoader,SimpleCache())
        val data=patterns.getPatternData(ULocale("en_US@hours=h23"),"gregorian")
        assertEquals("HH:mm",data.pattern(3))
        assertEquals("HH:mm:ss",data.pattern(2))
        val gb=patterns.getPatternData(ULocale("en_US@rg=gbzzzz"),"gregorian")
        assertEquals("HH:mm",gb.pattern(3))
        val persian=patterns.getPatternData(ULocale("fa_IR"),"persian")
        assertTrue(persian.pattern(3)!!.isNotEmpty())
    }
    @Test fun normalCalendarCallbackOrderAndPartialFailure() {
        val init=DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader)
        val core=DateTimePatternGeneratorCore(); val trace=ArrayList<String>()
        assertFailsWith<IllegalStateException> {
            init.initialize(core,ULocale.US) { locale ->
                assertEquals(ULocale.US,locale); assertTrue(core.isAvailableFormatSet("yMd")); trace.add("calendar")
                DateTimePatternInitializationCore.CalendarPatterns { style ->
                    trace.add("style:$style"); if(style==1) error("failed"); "glue:$style"
                }
            }
        }
        assertEquals(listOf("calendar","style:0","style:1"),trace)
        assertEquals("glue:0",core.getDateTimeFormat(0)); assertEquals("{1} {0}",core.getDateTimeFormat(1))
        assertEquals("?",core.getDecimal())
        val frozen=DateTimePatternGeneratorCore().freeze()
        assertFailsWith<UnsupportedOperationException> { init.initialize(frozen,ULocale.US) { error("unexpected calendar") } }
    }
}
