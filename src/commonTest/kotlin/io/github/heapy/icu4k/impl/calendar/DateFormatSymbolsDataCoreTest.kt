package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.test.*

class DateFormatSymbolsDataCoreTest {
    private fun owner()=object: DateFormatSymbolsDataCore.Owner {
        override fun className()="test.Symbols"
        override fun currentTimeMillis()=0L
        override fun defaultZoneOffset(time: Long)=0
    }
    private fun core()=DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader)
    @Test fun separatorCallbackCanTransformAndTriggerSourceFallback() {
        val data=object: DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader) {
            val calls=ArrayList<String?>()
            override fun setTimeSeparatorString(value: String?) {
                assertNotNull(capitalization); assertNotNull(validLocale); assertEquals("January",fields["months"]!![0])
                calls.add(value)
                if(calls.size==1) throw MissingResourceException("setter","test","separator")
                super.setTimeSeparatorString("custom$value")
            }
        }
        data.initialize(ULocale.US,null,"gregorian")
        assertEquals(listOf<String?>(":",":"),data.calls.toList()); assertEquals("custom:",data.timeSeparator)
    }
    @Test fun embeddedEnglishSymbolsAndOneBasedWeekdays() {
        val data=core(); data.initialize(ULocale.US,null,"gregorian")
        assertEquals("January",data.fields["months"]!![0]); assertEquals("Dec",data.fields["shortMonths"]!![11])
        assertEquals(listOf("BC","AD"),data.fields["eras"]!!.toList())
        assertEquals("",data.fields["weekdays"]!![0]); assertEquals("Sunday",data.fields["weekdays"]!![1]); assertEquals(8,data.fields["weekdays"]!!.size)
        assertEquals(listOf("AM","PM"),data.fields["ampms"]!!.toList())
        assertEquals(":",data.timeSeparator); assertEquals(DateFormatSymbolsDataCore.PATTERN_CHARS,data.localPatternChars)
        assertEquals(ULocale.US,data.requestedLocale)
    }
    @Test fun calendarAndLocaleFallbackAndCyclicLeapPatterns() {
        val data=core(); data.initialize(ULocale("zh_CN"),null,"chinese")
        assertEquals(60,data.fields["shortYearNames"]!!.size); assertEquals(12,data.fields["shortZodiacNames"]!!.size)
        val leap=data.fields["leapMonthPatterns"]!!; assertTrue(leap.any { it!=null })
        data.initialize(ULocale.US,null,"gregorian")
        assertSame(leap,data.fields["leapMonthPatterns"])
        val unknown=core(); unknown.initialize(ULocale.US,null,"UNKNOWN")
        assertContentEquals(data.fields["months"],unknown.fields["months"])
        val japanese=core(); japanese.initialize(ULocale.JAPAN,null,"japanese")
        assertEquals("令和",japanese.fields["eras"]!!.last())
        assertTrue(japanese.fields["eras"]!!.size>200)
    }
    @Test fun capitalizationDefaultRowsShareAndOwnerFailuresPropagate() {
        val data=core(); data.initialize(ULocale.ROOT,null,"gregorian")
        val rows=data.capitalization!!
        assertSame(rows[io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage.OTHER],rows[io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage.ZONE_LONG])
        assertContentEquals(booleanArrayOf(false,false),rows[io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage.OTHER])
        val broken=DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
            override fun className()="test.Symbols"
            override fun currentTimeMillis(): Long=error("clock")
            override fun defaultZoneOffset(time: Long): Int=error("unexpected offset")
        },ICUResourceBundleReader.embeddedLoader)
        assertEquals("clock",assertFailsWith<IllegalStateException> { broken.initialize(ULocale.US,null,"gregorian") }.message)
        assertTrue(broken.fields.isEmpty())
    }
}
