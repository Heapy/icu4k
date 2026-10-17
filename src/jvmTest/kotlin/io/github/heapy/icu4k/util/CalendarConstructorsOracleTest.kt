@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.Calendar as Source
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceTimeZone
import com.ibm.icu.util.SimpleTimeZone as SourceZone
import kotlin.test.*

class CalendarConstructorsOracleTest {
    private fun compare(a: Source,b: Calendar) {
        assertEquals(a.type,b.getType());assertEquals(a.timeZone.id,b.getTimeZone()!!.getID())
        assertEquals(a.timeInMillis,b.getTimeInMillis())
        for(f in 0 until a.fieldCount) assertEquals(a.get(f),b.get(f),"${a.type} field $f")
    }
    @Test fun dateAndFieldConstructorsMatchSourceUnderAlternateDefaultLocale() {
        val al=SourceLocale.getDefault(SourceLocale.Category.FORMAT);val bl=ULocale.getDefault(ULocale.Category.FORMAT)
        val az=SourceTimeZone.getDefault();val bz=TimeZone.getDefault()
        try {
            SourceTimeZone.setICUDefault(SourceZone(19800000,"private-default"));TimeZone.setICUDefault(SimpleTimeZone(19800000,"private-default"))
            for(id in listOf("en_US","am_ET@calendar=ethiopic-amete-alem")) {
                SourceLocale.setDefault(SourceLocale.Category.FORMAT,SourceLocale(id));ULocale.setDefault(ULocale.Category.FORMAT,ULocale(id))
                val pairs=listOf<Pair<Source,Calendar>>(
                    com.ibm.icu.util.BuddhistCalendar(java.util.Date(0)) to BuddhistCalendar(Date(0)),
                    com.ibm.icu.util.BuddhistCalendar(2000,1,3) to BuddhistCalendar(2000,1,3),
                    com.ibm.icu.util.BuddhistCalendar(2000,1,3,12,34,56) to BuddhistCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.TaiwanCalendar(java.util.Date(0)) to TaiwanCalendar(Date(0)),
                    com.ibm.icu.util.TaiwanCalendar(2000,1,3) to TaiwanCalendar(2000,1,3),
                    com.ibm.icu.util.TaiwanCalendar(2000,1,3,12,34,56) to TaiwanCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.PersianCalendar(java.util.Date(0)) to PersianCalendar(Date(0)),
                    com.ibm.icu.util.PersianCalendar(2000,1,3) to PersianCalendar(2000,1,3),
                    com.ibm.icu.util.PersianCalendar(2000,1,3,12,34,56) to PersianCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.IndianCalendar(java.util.Date(0)) to IndianCalendar(Date(0)),
                    com.ibm.icu.util.IndianCalendar(2000,1,3) to IndianCalendar(2000,1,3),
                    com.ibm.icu.util.IndianCalendar(2000,1,3,12,34,56) to IndianCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.HebrewCalendar(java.util.Date(0)) to HebrewCalendar(Date(0)),
                    com.ibm.icu.util.HebrewCalendar(2000,1,3) to HebrewCalendar(2000,1,3),
                    com.ibm.icu.util.HebrewCalendar(2000,1,3,12,34,56) to HebrewCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.CopticCalendar(java.util.Date(0)) to CopticCalendar(Date(0)),
                    com.ibm.icu.util.CopticCalendar(2000,1,3) to CopticCalendar(2000,1,3),
                    com.ibm.icu.util.CopticCalendar(2000,1,3,12,34,56) to CopticCalendar(2000,1,3,12,34,56),
                    com.ibm.icu.util.EthiopicCalendar(java.util.Date(0)) to EthiopicCalendar(Date(0)),
                    com.ibm.icu.util.EthiopicCalendar(2000,1,3) to EthiopicCalendar(2000,1,3),
                    com.ibm.icu.util.EthiopicCalendar(2000,1,3,12,34,56) to EthiopicCalendar(2000,1,3,12,34,56))
                for((a,b) in pairs) {
                    for(f in 0 until a.fieldCount) assertEquals(a.isSet(f),b.isSet(f),"${a.type} stamp $f")
                    compare(a,b)
                }
            }
        } finally {
            SourceLocale.setDefault(SourceLocale.Category.FORMAT,al);ULocale.setDefault(ULocale.Category.FORMAT,bl)
            SourceTimeZone.setICUDefault(az);TimeZone.setICUDefault(bz)
        }
    }
    @Test fun localeAndDefaultConstructors() {
        for(id in listOf("en_US@timezone=Asia/Tokyo","am_ET@calendar=ethiopic-amete-alem","en-u-tz-usnyc")) {
            val aLocale=SourceLocale(id);val bLocale=ULocale(id)
            val javaLocale=java.util.Locale.forLanguageTag(bLocale.toLanguageTag())
            val pairs=listOf<Pair<Source,Calendar>>(
                com.ibm.icu.util.BuddhistCalendar() to BuddhistCalendar(),
                com.ibm.icu.util.BuddhistCalendar(aLocale) to BuddhistCalendar(bLocale),
                com.ibm.icu.util.BuddhistCalendar(javaLocale) to BuddhistCalendar(javaLocale),
                com.ibm.icu.util.BuddhistCalendar(SourceZone(0,"explicit"),aLocale) to BuddhistCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.BuddhistCalendar(SourceZone(0,"explicit"),javaLocale) to BuddhistCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.TaiwanCalendar() to TaiwanCalendar(),
                com.ibm.icu.util.TaiwanCalendar(aLocale) to TaiwanCalendar(bLocale),
                com.ibm.icu.util.TaiwanCalendar(javaLocale) to TaiwanCalendar(javaLocale),
                com.ibm.icu.util.TaiwanCalendar(SourceZone(0,"explicit"),aLocale) to TaiwanCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.TaiwanCalendar(SourceZone(0,"explicit"),javaLocale) to TaiwanCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.PersianCalendar() to PersianCalendar(),
                com.ibm.icu.util.PersianCalendar(aLocale) to PersianCalendar(bLocale),
                com.ibm.icu.util.PersianCalendar(javaLocale) to PersianCalendar(javaLocale),
                com.ibm.icu.util.PersianCalendar(SourceZone(0,"explicit"),aLocale) to PersianCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.PersianCalendar(SourceZone(0,"explicit"),javaLocale) to PersianCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.IndianCalendar() to IndianCalendar(),
                com.ibm.icu.util.IndianCalendar(aLocale) to IndianCalendar(bLocale),
                com.ibm.icu.util.IndianCalendar(javaLocale) to IndianCalendar(javaLocale),
                com.ibm.icu.util.IndianCalendar(SourceZone(0,"explicit"),aLocale) to IndianCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.IndianCalendar(SourceZone(0,"explicit"),javaLocale) to IndianCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.HebrewCalendar() to HebrewCalendar(),
                com.ibm.icu.util.HebrewCalendar(aLocale) to HebrewCalendar(bLocale),
                com.ibm.icu.util.HebrewCalendar(javaLocale) to HebrewCalendar(javaLocale),
                com.ibm.icu.util.HebrewCalendar(SourceZone(0,"explicit"),aLocale) to HebrewCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.HebrewCalendar(SourceZone(0,"explicit"),javaLocale) to HebrewCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.CopticCalendar() to CopticCalendar(),
                com.ibm.icu.util.CopticCalendar(aLocale) to CopticCalendar(bLocale),
                com.ibm.icu.util.CopticCalendar(javaLocale) to CopticCalendar(javaLocale),
                com.ibm.icu.util.CopticCalendar(SourceZone(0,"explicit"),aLocale) to CopticCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.CopticCalendar(SourceZone(0,"explicit"),javaLocale) to CopticCalendar(SimpleTimeZone(0,"explicit"),javaLocale),
                com.ibm.icu.util.EthiopicCalendar() to EthiopicCalendar(),
                com.ibm.icu.util.EthiopicCalendar(aLocale) to EthiopicCalendar(bLocale),
                com.ibm.icu.util.EthiopicCalendar(javaLocale) to EthiopicCalendar(javaLocale),
                com.ibm.icu.util.EthiopicCalendar(SourceZone(0,"explicit"),aLocale) to EthiopicCalendar(SimpleTimeZone(0,"explicit"),bLocale),
                com.ibm.icu.util.EthiopicCalendar(SourceZone(0,"explicit"),javaLocale) to EthiopicCalendar(SimpleTimeZone(0,"explicit"),javaLocale))
            for((a,b) in pairs) {
                assertEquals(a.firstDayOfWeek,b.getFirstDayOfWeek());assertEquals(a.minimalDaysInFirstWeek,b.getMinimalDaysInFirstWeek())
                a.timeInMillis=1700000000000L;b.setTimeInMillis(1700000000000L);compare(a,b)
            }
        }
    }
    @Test fun fieldConstructorsDoNotCallClockSetter() {
        val ae=mutableListOf<Long>();val be=mutableListOf<Long>()
        object:com.ibm.icu.util.BuddhistCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { ae.add(value);super.setTimeInMillis(value) } }
        object:BuddhistCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { be.add(value);super.setTimeInMillis(value) } }
        object:com.ibm.icu.util.TaiwanCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { ae.add(value);super.setTimeInMillis(value) } }
        object:TaiwanCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { be.add(value);super.setTimeInMillis(value) } }
        object:com.ibm.icu.util.PersianCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { ae.add(value);super.setTimeInMillis(value) } }
        object:PersianCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { be.add(value);super.setTimeInMillis(value) } }
        object:com.ibm.icu.util.IndianCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { ae.add(value);super.setTimeInMillis(value) } }
        object:IndianCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { be.add(value);super.setTimeInMillis(value) } }
        object:com.ibm.icu.util.HebrewCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { ae.add(value);super.setTimeInMillis(value) } }
        object:HebrewCalendar(2000,1,3) { override fun setTimeInMillis(value: Long) { be.add(value);super.setTimeInMillis(value) } }
        assertEquals(emptyList(),ae);assertEquals(ae,be)
    }
}
