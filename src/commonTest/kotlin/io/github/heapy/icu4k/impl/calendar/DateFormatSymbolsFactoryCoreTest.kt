package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CacheBase
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateFormatSymbolsFactoryCoreTest {
    private fun blank()=DateFormatSymbolsStateCore(DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
        override fun className()="test.Symbols"
        override fun currentTimeMillis()=0L
        override fun defaultZoneOffset(time: Long)=0
    },ICUResourceBundleReader.embeddedLoader))
    @Test fun templatesAreSharedButResultsAndSetterSlotsAreFresh() {
        var templates=0; var results=0; var defaults=0
        val retained=ArrayList<DateFormatSymbolsStateCore>()
        val factory=DateFormatSymbolsFactoryCore({ templates++; blank().also { retained.add(it) } },{ results++; blank() },{ defaults++; ULocale.US })
        val a=factory.getInstance(); val b=factory.getInstance(ULocale.US)
        assertEquals(1,templates); assertEquals(2,results); assertEquals(1,defaults)
        assertNotSame(a,b); assertSame(a.data.fields["months"],b.data.fields["months"])
        assertSame(a.data.capitalization,b.data.capitalization)
        a.setMonths(arrayOf("changed")); assertEquals("January",b.getMonths()[0])
        val c=factory.getInstance(ULocale.US); assertSame(b.data.fields["months"],c.data.fields["months"])
        factory.getInstance(ULocale("en_US@numbers=arab")); assertEquals(2,templates)
        factory.getInstance(ULocale("en_US@numbers=latn")); assertEquals(3,templates)
    }
    @Test fun ignoredKeywordsKeepTheFirstPublishedRequestedLocaleAndLeapAliases() {
        val retained=ArrayList<DateFormatSymbolsStateCore>()
        val factory=DateFormatSymbolsFactoryCore({ blank().also { retained.add(it) } })
        val first=factory.getInstance(ULocale("en_US@hours=h23")); val next=factory.getInstance(ULocale.US)
        assertEquals("en_US@hours=h23",next.data.requestedLocale.toString()); assertSame(first.data.fields["months"],next.data.fields["months"])
        val leap=factory.forCalendarType(ULocale.US,"chinese")
        leap.setLeapMonthPattern("shared",0,0)
        assertEquals("shared",factory.forCalendarType(ULocale.US,"chinese").getLeapMonthPattern(0,0))
        val direct=factory.fromBundle(ULocale("en_US@calendar=chinese"),null)
        assertNotEquals("shared",direct.getLeapMonthPattern(0,0))
    }
    @Test fun cacheKeysNullTypesAndTypeExtractionMatchSource() {
        lateinit var factory: DateFormatSymbolsFactoryCore
        val keys=ArrayList<String>()
        val cache=object: SoftCache<String,DateFormatSymbolsStateCore,ULocale?>() {
            override fun createInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore { keys.add(key); return factory.createTemplate(key,data) }
        }
        factory=DateFormatSymbolsFactoryCore({ blank() },cache=cache)
        val nullType=factory.forCalendarType(ULocale.US,null)
        assertEquals("January",nullType.getMonths()[0]); assertEquals(listOf("en_US+null"),keys)
        factory.forCalendarType(ULocale("en_US@numbers=arab"),"chinese+tail")
        assertEquals("en_US+chinese+tail+arab",keys.last())
        assertNotNull(factory.createTemplate("en_US+chinese+tail",ULocale.US).getYearNames(0,0))
        assertEquals("January",factory.createTemplate("no-plus",ULocale.US).getMonths()[0])
        assertFailsWith<NullPointerException> { factory.getInstance(null) }
    }
    @Test fun failedAndNullCacheResultsDoNotPartiallyCopyTargetState() {
        var mode=0; var reads=0
        val cache=object: CacheBase<String,DateFormatSymbolsStateCore,ULocale?>() {
            override fun getInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore? { reads++; if(mode==0) error("read"); return null }
            override fun createInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore?=error("unused")
        }
        val factory=DateFormatSymbolsFactoryCore({ blank() },cache=cache)
        val target=blank(); target.setMonths(arrayOf("retained"))
        assertFailsWith<IllegalStateException> { factory.initializeData(target,ULocale.US,"gregorian") }
        mode=1; assertFailsWith<NullPointerException> { factory.initializeData(target,ULocale.US,"gregorian") }
        assertContentEquals(arrayOf<String?>("retained"),target.getMonths()); assertEquals(2,reads)
    }
    @Test fun resultCopyBypassesSeparatorOverrideAndClassNamesSelectCalendarData() {
        var setters=0
        fun result()=DateFormatSymbolsStateCore(object: DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
            override fun className()="test.Symbols"
            override fun currentTimeMillis(): Long=error("unexpected result initialization")
            override fun defaultZoneOffset(time: Long): Int=error("unexpected result initialization")
        },ICUResourceBundleReader.embeddedLoader) {
            override fun setTimeSeparatorString(value: String?) { setters++; error("unexpected setter") }
        })
        val factory=DateFormatSymbolsFactoryCore({ blank() },{ result() })
        val japanese=factory.forCalendarClassName(ULocale.JAPAN,"com.example.JapaneseCalendar")
        assertEquals("令和",japanese.getEras().last())
        val dangi=factory.forCalendarClassName(ULocale.US,"com.example.DangiCalendar")
        assertNotNull(dangi.getYearNames(0,0))
        assertEquals(0,setters)
        assertFailsWith<NullPointerException> { factory.forCalendarClassName(ULocale.US,null) }
    }
}
