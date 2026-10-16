package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CalendarLocaleCoreTest {
    private class Model(val type: CalType,val constructorZone: String) {
        var zone=constructorZone
        var first=7
        var minimal=1
        var amete=false
        var time=99L
    }
    private class Fixture : CalendarFactoryCore.Owner<Model,String> {
        val trace=ArrayList<String>()
        var count=0
        var failZoneSetter=false
        var model: Model?=null
        val core=CalendarFactoryCore(this)
        override fun defaultFormatLocale(): ULocale { trace.add("default"); return ULocale("en_US@calendar=iso8601") }
        override fun forULocaleOrDefault(locale: ULocale): String { trace.add("zone"); return "zone${++count}" }
        override fun construct(type: CalType,zone: String,locale: ULocale): Model {
            trace.add("construct:$type:$zone"); return Model(type,zone).also { model=it }
        }
        override fun setAmeteAlemEra(calendar: Model,value: Boolean) { trace.add("amete:$value"); calendar.amete=value }
        override fun setFirstDayOfWeek(calendar: Model,day: Int) { trace.add("first:$day"); calendar.first=day }
        override fun setMinimalDaysInFirstWeek(calendar: Model,days: Int) { trace.add("minimal:$days"); calendar.minimal=days }
        override fun setTimeZone(calendar: Model,zone: String) { trace.add("setZone:$zone"); if(failZoneSetter) error("zone"); calendar.zone=zone }
        override fun currentTimeMillis(): Long { trace.add("clock"); return 123 }
        override fun setTimeInMillis(calendar: Model,time: Long) { trace.add("time:$time"); calendar.time=time }
    }

    @Test fun realPreferencesKeywordsCanonicalVariantsAndLocaleMetadata() {
        assertEquals("buddhist",CalendarUtil.getCalendarType(ULocale("th")))
        assertEquals("persian",CalendarUtil.getCalendarType(ULocale("en@rg=irzzzz")))
        assertEquals("gregorian",CalendarUtil.getCalendarType(ULocale("ja_JP_TRADITIONAL")))
        assertEquals("buddhist",CalendarUtil.getCalendarType(ULocale("th_TH_TRADITIONAL")))
        assertEquals("unknown",CalendarUtil.getCalendarType(ULocale("en_US@calendar=UNKNOWN")))
        assertEquals("i\u0307ς",CalendarUtil.getCalendarType(ULocale("en_US@calendar=İΣ")))
        assertNull(CalendarLocaleCore.getCalendarTypeForLocale(ULocale("en_US@calendar=UNKNOWN")))
        val locale=ULocale("sr_Latn_RS")
        assertSame(locale,CalendarLocaleCore.calendarLocale(locale))
        assertEquals("en_US@calendar=iso8601",CalendarLocaleCore.calendarLocale(ULocale("en_US_POSIX@calendar=iso8601;fw=fri;rg=gbzzzz")).toString())
        assertEquals("GB",CalendarLocaleCore.getRegionForCalendar(ULocale("en_US@rg=gbzzzz")))
        assertNull(CalendarLocaleCore.firstDayOverride(ULocale("en@fw=MON")))
        assertEquals(2,CalendarLocaleCore.firstDayOverride(ULocale("en@fw=mon")))
        val preferred=CalendarLocaleCore.getKeywordValuesForLocale("ignored",ULocale("th"),true)
        assertEquals(listOf("buddhist","gregorian"),preferred.toList())
        val all=CalendarLocaleCore.getKeywordValuesForLocale(null,ULocale("th"),false)
        assertEquals(preferred.toList(),all.take(preferred.size))
        assertEquals(CalType.entries.map { it.getId() }.toSet(),all.toSet())
        assertEquals(all.size,all.toSet().size)
        preferred[0]="changed"
        assertEquals("buddhist",CalendarLocaleCore.getKeywordValuesForLocale(null,ULocale("th"),true)[0])
    }

    @Test fun factoryDefaultOrderAndExplicitZoneStillPerformConstructorLookup() {
        val f=Fixture(); val result=f.core.getInstance(null,null)
        assertEquals("zone2",result.constructorZone); assertEquals("zone1",result.zone)
        assertEquals(123L,result.time)
        assertEquals(listOf("default","zone","zone","construct:GREGORIAN:zone2","first:2","minimal:4","setZone:zone1","clock","time:123"),f.trace)
        val explicit=Fixture(); val model=explicit.core.getInstance("chosen",ULocale("en_US"))
        assertEquals("zone1",model.constructorZone); assertEquals("chosen",model.zone)
        assertEquals(listOf("zone","construct:GREGORIAN:zone1","setZone:chosen","clock","time:123"),explicit.trace)
    }

    @Test fun specialTypesIsoOverridesAndFailureOrdering() {
        for(suffix in listOf("fw=fri","fw=INVALID","rg=gbzzzz","rg=INVALID")) {
            val f=Fixture(); val model=f.core.createInstance(ULocale("en_US@calendar=iso8601;$suffix"))
            assertEquals(7,model.first); assertEquals(4,model.minimal)
            assertFalse(f.trace.any { it.startsWith("first:") })
            assertFalse("clock" in f.trace)
        }
        val ethiopic=Fixture(); val model=ethiopic.core.createInstance(ULocale("en_US@calendar=ethiopic-amete-alem"))
        assertEquals(CalType.ETHIOPIC,model.type); assertTrue(model.amete)
        for(type in listOf("islamic-civil","islamic-rgsa","islamic-tbla","islamic-umalqura")) {
            assertEquals(CalType.ISLAMIC,Fixture().core.createInstance(ULocale("en_US@calendar=$type")).type)
        }
        assertEquals(CalType.GREGORIAN,Fixture().core.createInstance(ULocale("en_US@calendar=unknown")).type)
        val broken=Fixture(); broken.failZoneSetter=true
        assertFailsWith<IllegalStateException> { broken.core.getInstance("chosen",ULocale("en_US@calendar=iso8601")) }
        assertEquals(4,broken.model!!.minimal)
        assertEquals("zone1",broken.model!!.zone)
        assertFalse("clock" in broken.trace)
        assertEquals(99L,broken.model!!.time)
    }
}
