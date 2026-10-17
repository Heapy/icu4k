@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateFormatSymbols as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.ICUData
import kotlin.test.*

class DateFormatSymbolsOracleTest {
    private fun outcome(block:()->Any?): Any? = try {
        val value=block();if(value is Array<*>) value.toList() else value
    } catch(e:RuntimeException) { e.javaClass.simpleName+":"+e.message }
    private fun compare(a:Source,b:DateFormatSymbols) {
        assertContentEquals(a.eras,b.getEras());assertContentEquals(a.eraNames,b.getEraNames());assertContentEquals(a.narrowEras,b.getNarrowEras())
        assertEquals(a.timeSeparatorString,b.getTimeSeparatorString());assertEquals(a.localPatternChars,b.getLocalPatternChars())
        for(c in -1..3) for(w in -1..4) {
            assertEquals(outcome{a.getMonths(c,w)},outcome{b.getMonths(c,w)},"months $c/$w")
            assertEquals(outcome{a.getWeekdays(c,w)},outcome{b.getWeekdays(c,w)},"weekdays $c/$w")
            assertEquals(outcome{a.getQuarters(c,w)},outcome{b.getQuarters(c,w)},"quarters $c/$w")
            assertEquals(outcome{a.getAmPmStrings(c,w)},outcome{b.getAmPmStrings(c,w)},"ampm $c/$w")
            assertEquals(outcome{a.getYearNames(c,w)},outcome{b.getYearNames(c,w)})
            assertEquals(outcome{a.getZodiacNames(c,w)},outcome{b.getZodiacNames(c,w)})
            assertEquals(outcome{a.getLeapMonthPattern(c,w)},outcome{b.getLeapMonthPattern(c,w)})
        }
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE).toString(),b.getLocale(ULocale.VALID_LOCALE).toString())
        assertEquals(a.getLocale(SourceLocale.ACTUAL_LOCALE).toString(),b.getLocale(ULocale.ACTUAL_LOCALE).toString())
    }
    @Test fun allCalendarTypesAndLocaleData() {
        for(id in listOf("en_US","fr_FR","ar_EG","ja_JP","zh_TW","he_IL")) for(type in CalType.entries) {
            val locale="$id@calendar=${type.getId()}"
            compare(Source(SourceLocale(locale)),DateFormatSymbols(ULocale(locale)))
        }
        for(id in listOf("en@numbers=arab","ar@numbers=latn","fr_CH","en_US_UNKNOWN")) compare(Source(SourceLocale(id)),DateFormatSymbols(ULocale(id)))
    }
    @Test fun mutationCloneAndSharedLeapPatterns() {
        for(type in listOf("gregorian","chinese","hebrew")) {
            val id="en_US_DFSSYMBOLS@calendar=$type"
            val a=Source(SourceLocale(id));val b=DateFormatSymbols(ULocale(id))
            val ac=a.clone();val bc=b.clone()
            assertEquals(a.hashCode(),b.hashCode());assertEquals(a==ac,b==bc)
            ac.setMonths(arrayOf("custom",null));bc.setMonths(arrayOf("custom",null));assertEquals(a==ac,b==bc)
            assertContentEquals(ac.months,bc.getMonths());assertNotEquals(a.months.toList(),ac.months.toList())
            for(c in -1..3) for(w in -1..4) {
                val av=a.clone();val bv=b.clone()
                assertEquals(outcome{av.setWeekdays(arrayOf("x"),c,w)},outcome{bv.setWeekdays(arrayOf("x"),c,w)})
                assertEquals(outcome{av.getWeekdays(c,w)},outcome{bv.getWeekdays(c,w)})
                assertEquals(outcome{av.setLeapMonthPattern("shared",c,w)},outcome{bv.setLeapMonthPattern("shared",c,w)})
                assertEquals(outcome{a.getLeapMonthPattern(c,w)},outcome{b.getLeapMonthPattern(c,w)})
            }
        }
    }
    @Test fun calendarClassBundleAndJvmAdapters() {
        val aLocale=SourceLocale("en_US");val bLocale=ULocale("en_US")
        compare(Source(com.ibm.icu.util.JapaneseCalendar(),aLocale),DateFormatSymbols(JapaneseCalendar(),bLocale))
        compare(Source(com.ibm.icu.util.TaiwanCalendar::class.java,aLocale),DateFormatSymbols(TaiwanCalendar::class,bLocale))
        compare(Source(com.ibm.icu.util.HebrewCalendar::class.java,java.util.Locale.FRANCE),DateFormatSymbols(HebrewCalendar::class.java,java.util.Locale.FRANCE))
        compare(Source(java.util.Locale.JAPAN),DateFormatSymbols(java.util.Locale.JAPAN))
        val ab=com.ibm.icu.util.UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"en")
        val bb=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"en")
        compare(Source(ab,aLocale),DateFormatSymbols(bb,bLocale))
        assertEquals(Source.getAvailableULocales().toList().map{it.toString()},DateFormatSymbols.getAvailableULocales().map{it.toString()})
        assertEquals(Source.getAvailableLocales().toList(),DateFormatSymbols.getAvailableLocales().toList())
        assertNull(DateFormatSymbols.getDateFormatBundle(null as Calendar?,null as ULocale?))
    }
    @Test fun lazyZoneMatrixAndDefensiveCopies() {
        val a=Source(SourceLocale.US);val b=DateFormatSymbols(ULocale.US)
        val az=a.zoneStrings;val bz=b.getZoneStrings()
        assertEquals(az.map{it.toList()},bz.map{it!!.toList()})
        az[0][1]="first-call";bz[0]!![1]="first-call"
        assertEquals(a.zoneStrings[0][1],b.getZoneStrings()[0]!![1])
        val ac=a.zoneStrings;val bc=b.getZoneStrings();ac[0][1]="copy";bc[0]!![1]="copy"
        assertEquals("first-call",a.zoneStrings[0][1]);assertEquals("first-call",b.getZoneStrings()[0]!![1])
        a.zoneStrings=arrayOf(arrayOf("id","standard",null));b.setZoneStrings(arrayOf(arrayOf("id","standard",null)))
        assertEquals(a.zoneStrings.map{it.toList()},b.getZoneStrings().map{it!!.toList()})
    }
    @Test fun protectedEraInitializerUsesLiveMapAndBundleConstructor() {
        val aEvents=mutableListOf<String>();val bEvents=mutableListOf<String>()
        val ab=com.ibm.icu.util.UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"en")
        val bb=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"en")
        val a=object:Source(ab,SourceLocale.US) {
            override fun initEras(key: String,maps: MutableMap<String,MutableMap<String,String>>,bundle: com.ibm.icu.impl.ICUResourceBundle?,maxEra: Int): Array<String>? {
                aEvents.add(key);maps[key]=mutableMapOf("0" to "override")
                return super.initEras(key,maps,bundle,maxEra)
            }
        }
        val b=object:DateFormatSymbols(bb,ULocale.US) {
            override fun initEras(key: String,maps: MutableMap<String,MutableMap<String,String>>,bundle: io.github.heapy.icu4k.impl.ICUResourceBundle?,maxEra: Int): Array<String?>? {
                bEvents.add(key);maps[key]=mutableMapOf("0" to "override")
                return super.initEras(key,maps,bundle,maxEra)
            }
        }
        assertEquals(aEvents,bEvents);assertEquals(3,aEvents.size);compare(a,b);assertEquals("override",b.getEras()[0])
    }

    @Test fun directFactoriesDefaultsAndRejectedJavaBundles() {
        val al=SourceLocale.getDefault(SourceLocale.Category.FORMAT);val bl=ULocale.getDefault(ULocale.Category.FORMAT)
        try {
            SourceLocale.setDefault(SourceLocale.Category.FORMAT,SourceLocale("fr_FR_DFSDEFAULT"))
            ULocale.setDefault(ULocale.Category.FORMAT,ULocale("fr_FR_DFSDEFAULT"))
            compare(Source.getInstance(),DateFormatSymbols.getInstance())
            compare(Source.getInstance(java.util.Locale.FRANCE),DateFormatSymbols.getInstance(java.util.Locale.FRANCE))
            assertNotSame(DateFormatSymbols.getInstance(),DateFormatSymbols.getInstance())
            val javaBundle=object:java.util.ListResourceBundle() {
                override fun getContents(): Array<Array<Any>> = arrayOf(arrayOf("key","value"))
            }
            assertFailsWith<ClassCastException> { Source(javaBundle,SourceLocale.US) }
            assertFailsWith<ClassCastException> { DateFormatSymbols(javaBundle,ULocale.US) }
            assertFailsWith<NullPointerException> { Source(null as SourceLocale?) }
            assertFailsWith<NullPointerException> { DateFormatSymbols(null as ULocale?) }
        } finally { SourceLocale.setDefault(SourceLocale.Category.FORMAT,al);ULocale.setDefault(ULocale.Category.FORMAT,bl) }
    }

}
