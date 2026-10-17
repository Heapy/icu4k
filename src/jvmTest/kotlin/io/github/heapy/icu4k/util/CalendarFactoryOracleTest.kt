@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.Calendar as Source
import com.ibm.icu.util.SimpleTimeZone as SourceZone
import com.ibm.icu.util.TimeZone as SourceTimeZone
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.CalType
import kotlin.test.*

class CalendarFactoryOracleTest {
    private fun compare(a: Source,b: Calendar) {
        assertEquals(a.javaClass.simpleName,b.javaClass.simpleName)
        assertEquals(a.type,b.getType());assertEquals(a.firstDayOfWeek,b.getFirstDayOfWeek())
        assertEquals(a.minimalDaysInFirstWeek,b.getMinimalDaysInFirstWeek())
        assertEquals(a.timeZone.id,b.getTimeZone()!!.getID())
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE).toString(),b.getLocale(ULocale.VALID_LOCALE).toString())
        a.timeInMillis=1700000000000L;b.setTimeInMillis(1700000000000L)
        for(f in 0 until a.fieldCount) assertEquals(a.get(f),b.get(f),"${a.type} field $f")
    }
    @Test fun everyCalendarTypeAndUnknownFallback() {
        for(type in CalType.entries.map { it.getId() }+listOf("unknown","ISLAMIC-CIVIL")) for(region in listOf("en_US","ar_SA","th_TH")) {
            val id="$region@calendar=$type;timezone=Asia/Tokyo"
            compare(Source.getInstance(SourceLocale(id)),Calendar.getInstance(ULocale(id)))
            val az=SourceZone(19800000,"explicit");val bz=SimpleTimeZone(19800000,"explicit")
            val a=Source.getInstance(az,SourceLocale(id));val b=Calendar.getInstance(bz,ULocale(id))
            assertSame(az,a.timeZone);assertSame(bz,b.getTimeZone());compare(a,b)
        }
    }
    @Test fun localePreferencesAndIsoWeekOverrides() {
        for(id in listOf("en_US","th_TH","fa_IR","am_ET","ar_SA","zh_TW","he_IL","ja_JP", "en_US@calendar=iso8601", "en_US@calendar=iso8601;fw=sun", "en_US@calendar=iso8601;rg=gbzzzz", "en_GB@calendar=iso8601;rg=uszzzz", "en_US@calendar=iso8601;fw=invalid")) {
            compare(Source.getInstance(SourceLocale(id)),Calendar.getInstance(ULocale(id)))
            val locale=java.util.Locale.forLanguageTag(ULocale(id).toLanguageTag())
            compare(Source.getInstance(locale),Calendar.getInstance(locale))
            compare(Source.getInstance(SourceZone(0,"explicit"),locale),Calendar.getInstance(SimpleTimeZone(0,"explicit"),locale))
        }
    }
    @Test fun defaultAndNullOverloadsResolveFormatLocale() {
        val al=SourceLocale.getDefault(SourceLocale.Category.FORMAT);val bl=ULocale.getDefault(ULocale.Category.FORMAT)
        val az=SourceTimeZone.getDefault();val bz=TimeZone.getDefault()
        try {
            SourceLocale.setDefault(SourceLocale.Category.FORMAT,SourceLocale("th_TH@timezone=Asia/Tokyo"))
            ULocale.setDefault(ULocale.Category.FORMAT,ULocale("th_TH@timezone=Asia/Tokyo"))
            SourceTimeZone.setICUDefault(SourceZone(3600000,"default"));TimeZone.setICUDefault(SimpleTimeZone(3600000,"default"))
            compare(Source.getInstance(),Calendar.getInstance())
            compare(Source.getInstance(null as SourceLocale?),Calendar.getInstance(null as ULocale?))
            compare(Source.getInstance(null as SourceTimeZone?),Calendar.getInstance(null as TimeZone?))
            compare(Source.getInstance(null as SourceTimeZone?,null as SourceLocale?),Calendar.getInstance(null as TimeZone?,null as ULocale?))
        } finally {
            SourceLocale.setDefault(SourceLocale.Category.FORMAT,al);ULocale.setDefault(ULocale.Category.FORMAT,bl)
            SourceTimeZone.setICUDefault(az);TimeZone.setICUDefault(bz)
        }
    }
    @Test fun availableLocaleArraysMatch() {
        assertEquals(Source.getAvailableULocales().map{it.toString()},Calendar.getAvailableULocales().map{it.toString()})
        assertEquals(Source.getAvailableLocales().toList(),Calendar.getAvailableLocales().toList())
    }
    @Test fun constructionResolvesItsOwnLocaleZoneAndDefersOffsets() {
        val az=SourceTimeZone.getDefault();val bz=TimeZone.getDefault()
        val ae=mutableListOf<String>();val be=mutableListOf<String>()
        fun sourceZone(label: String)=object:SourceZone(0,label) {
            override fun cloneAsThawed(): SourceZone { ae.add("$label:clone");return super.cloneAsThawed() }
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { ae.add("$label:offset");super.getOffset(date,local,offsets) }
        }
        fun portZone(label: String)=object:SimpleTimeZone(0,label) {
            override fun cloneAsThawed(): SimpleTimeZone { be.add("$label:clone");return super.cloneAsThawed() }
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { be.add("$label:offset");super.getOffset(date,local,offsets) }
        }
        try {
            SourceTimeZone.setICUDefault(sourceZone("default").freeze());TimeZone.setICUDefault(portZone("default").freeze())
            for(explicit in listOf(false,true)) {
                ae.clear();be.clear()
                Source.getInstance(if(explicit) sourceZone("explicit") else null,SourceLocale("en_US@calendar=gregorian"))
                Calendar.getInstance(if(explicit) portZone("explicit") else null,ULocale("en_US@calendar=gregorian"))
                assertEquals(ae,be)
                assertEquals(List(if(explicit) 1 else 2) { "default:clone" },ae)
            }
        } finally { SourceTimeZone.setICUDefault(az);TimeZone.setICUDefault(bz) }
    }

}
