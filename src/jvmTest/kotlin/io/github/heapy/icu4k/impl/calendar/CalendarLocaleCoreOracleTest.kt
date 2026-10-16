package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.TimeZone as SourceZone
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class CalendarLocaleCoreOracleTest {
    private fun ids(): List<String> {
        val regions=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"supplementalData")["calendarPreferenceData"].keySet()
        return listOf("","root","und","und_ZZ","th","fa","ja_JP_TRADITIONAL","th_TH_TRADITIONAL","en_US_POSIX","zh_Hant_TW","und@rg=irzzzz","en@sd=th10","en_US@calendar=UNKNOWN","en_US@calendar=İΣ","en_US@fw=MON","en_US@fw=mon","en_US@rg=gbzzzz","en_US@calendar=iso8601;fw=fri","en_US@calendar=iso8601;rg=uszzzz")+
            regions.map { "und_$it" }+CalType.entries.map { "en_US@calendar=${it.getId()}" }
    }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        e.javaClass.name to if(e is NullPointerException || e is IndexOutOfBoundsException) null else e.message
    }
    @Test fun enumAndLocalePreferencesMatchSource() {
        assertEquals(com.ibm.icu.impl.CalType.values().map { it.name to it.id },CalType.entries.map { it.name to it.getId() })
        for(id in ids()) {
            val source=SourceLocale(id); val target=ULocale(id)
            assertEquals(com.ibm.icu.impl.CalendarUtil.getCalendarType(source),CalendarUtil.getCalendarType(target),id)
            for(common in listOf(false,true)) for(key in listOf(null,"calendar","ignored")) {
                assertEquals(SourceCalendar.getKeywordValuesForLocale(key,source,common).toList(),CalendarLocaleCore.getKeywordValuesForLocale(key,target,common).toList(),id)
            }
            val calendar=com.ibm.icu.util.GregorianCalendar(SourceZone.getTimeZone("Etc/GMT"),source)
            assertEquals(calendar.getLocale(SourceLocale.VALID_LOCALE).toString(),CalendarLocaleCore.calendarLocale(target).toString(),id)
            assertEquals(calendar.getLocale(SourceLocale.ACTUAL_LOCALE).toString(),CalendarLocaleCore.calendarLocale(target).toString(),id)
            assertEquals(SourceLocale.getRegionForSupplementalData(source,true).ifEmpty { "001" },CalendarLocaleCore.getRegionForCalendar(target),id)
            CalendarLocaleCore.firstDayOverride(target)?.let { assertEquals(calendar.firstDayOfWeek,it,id) }
        }
        assertEquals(outcome { com.ibm.icu.impl.CalendarUtil.getCalendarType(null) },outcome { CalendarUtil.getCalendarType(null) })
        assertEquals(outcome { SourceCalendar.getKeywordValuesForLocale(null,null,true) },outcome { CalendarLocaleCore.getKeywordValuesForLocale(null,null,true) })
    }
    private class Binding : CalendarFactoryCore.Owner<SourceCalendar,SourceZone> {
        val trace=ArrayList<String>()
        override fun defaultFormatLocale(): ULocale { trace.add("defaultLocale"); return ULocale("en_US") }
        override fun forULocaleOrDefault(locale: ULocale): SourceZone {
            trace.add("zone:$locale")
            return SourceZone.forULocaleOrDefault(SourceLocale(locale.toString()))
        }
        override fun construct(type: CalType,zone: SourceZone,locale: ULocale): SourceCalendar {
            trace.add("construct:$type")
            val l=SourceLocale(locale.toString())
            return when(type) {
                CalType.GREGORIAN -> com.ibm.icu.util.GregorianCalendar(zone,l)
                CalType.BUDDHIST -> com.ibm.icu.util.BuddhistCalendar(zone,l)
                CalType.CHINESE -> com.ibm.icu.util.ChineseCalendar(zone,l)
                CalType.COPTIC -> com.ibm.icu.util.CopticCalendar(zone,l)
                CalType.DANGI -> com.ibm.icu.util.DangiCalendar(zone,l)
                CalType.ETHIOPIC -> com.ibm.icu.util.EthiopicCalendar(zone,l)
                CalType.HEBREW -> com.ibm.icu.util.HebrewCalendar(zone,l)
                CalType.INDIAN -> com.ibm.icu.util.IndianCalendar(zone,l)
                CalType.ISLAMIC -> com.ibm.icu.util.IslamicCalendar(zone,l)
                CalType.JAPANESE -> com.ibm.icu.util.JapaneseCalendar(zone,l)
                CalType.PERSIAN -> com.ibm.icu.util.PersianCalendar(zone,l)
                CalType.ROC -> com.ibm.icu.util.TaiwanCalendar(zone,l)
                else -> error("Factory must normalize constructor dispatch: $type")
            }
        }
        override fun setAmeteAlemEra(calendar: SourceCalendar,value: Boolean) { trace.add("amete:$value"); (calendar as com.ibm.icu.util.EthiopicCalendar).isAmeteAlemEra=value }
        override fun setFirstDayOfWeek(calendar: SourceCalendar,day: Int) { trace.add("first:$day"); calendar.firstDayOfWeek=day }
        override fun setMinimalDaysInFirstWeek(calendar: SourceCalendar,days: Int) { trace.add("minimal:$days"); calendar.minimalDaysInFirstWeek=days }
        override fun setTimeZone(calendar: SourceCalendar,zone: SourceZone) { trace.add("setZone"); calendar.timeZone=zone }
        override fun currentTimeMillis(): Long { trace.add("clock"); return 0 }
        override fun setTimeInMillis(calendar: SourceCalendar,time: Long) { trace.add("time:$time"); calendar.timeInMillis=time }
    }
    @Test fun factorySelectionEveryCalendarAndIsoWeekOverrides() {
        for(id in ids()+listOf("en_US@calendar=iso8601;fw=INVALID","en_US@calendar=iso8601;rg=INVALID","th_TH@timezone=America/New_York")) {
            for(explicitZone in listOf(false,true)) {
                val owner=Binding(); val core=CalendarFactoryCore(owner)
                val zone=if(explicitZone) SourceZone.getTimeZone("Etc/GMT+5") else null
                val source=SourceCalendar.getInstance(zone,SourceLocale(id)).apply { timeInMillis=0 }
                val target=core.getInstance(zone,ULocale(id))
                assertEquals(source.javaClass,target.javaClass,id)
                assertEquals(source.type,target.type,id)
                assertEquals(source.timeZone.id,target.timeZone.id,id)
                assertEquals(source.weekData,target.weekData,id)
                assertEquals(source.getLocale(SourceLocale.VALID_LOCALE),target.getLocale(SourceLocale.VALID_LOCALE),id)
                assertEquals((0..23).map { source.get(it) },(0..23).map { target.get(it) },id)
                assertEquals(if(explicitZone) 1 else 2,owner.trace.count { it.startsWith("zone:") },id)
                assertEquals(listOf("setZone","clock","time:0"),owner.trace.takeLast(3),id)
                if(explicitZone) assertSame(zone,target.timeZone)
            }
        }
        val binding=Binding(); CalendarFactoryCore(binding).getInstance(null,null)
        assertEquals("defaultLocale",binding.trace.first())
        assertEquals(2,binding.trace.count { it.startsWith("zone:") })
    }
}
