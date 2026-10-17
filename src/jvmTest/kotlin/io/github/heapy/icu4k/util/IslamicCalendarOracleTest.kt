package io.github.heapy.icu4k.util

import com.ibm.icu.util.IslamicCalendar as Source
import com.ibm.icu.util.SimpleTimeZone as SourceZone
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.test.*

class IslamicCalendarOracleTest {
    private fun compare(a: Source, b: IslamicCalendar) {
        assertEquals(a.timeInMillis, b.getTimeInMillis())
        assertEquals(a.type, b.getType())
        assertEquals(a.isCivil, b.isCivil())
        for (f in 0 until a.fieldCount) assertEquals(a.get(f), b.get(f), "field $f ${a.type}")
        assertEquals(a.relatedYear, b.getRelatedYear())
        assertEquals(a.inTemporalLeapYear(), b.inTemporalLeapYear())
    }
    @Test fun modesBoundariesAndArithmetic() {
        for (mode in IslamicCalendar.CalculationType.entries) for (offset in listOf(0, 19800000)) {
            val a = Source(SourceZone(offset, "test"), SourceLocale.ROOT)
            val b = IslamicCalendar(SimpleTimeZone(offset, "test"), ULocale.ROOT)
            a.calculationType = Source.CalculationType.valueOf(mode.name); b.setCalculationType(mode)
            for (year in listOf(1299, 1300, 1445, 1600, 1601)) for (month in 0..11) {
                a.clear(); b.clear(); a.set(year, month, 29); b.set(year, month, 29); compare(a,b)
                for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE)) {
                    val ac = a.clone() as Source; val bc = b.clone() as IslamicCalendar
                    ac.add(field, 2); bc.add(field, 2); compare(ac,bc)
                    ac.roll(field, -1); bc.roll(field, -1); compare(ac,bc)
                }
            }
            for (f in 0 until a.fieldCount) {
                assertEquals(a.getMinimum(f),b.getMinimum(f)); assertEquals(a.getMaximum(f),b.getMaximum(f))
                assertEquals(a.getGreatestMinimum(f),b.getGreatestMinimum(f)); assertEquals(a.getLeastMaximum(f),b.getLeastMaximum(f))
            }
        }
    }
    @Test fun localeModesTransitionsAndClones() {
        for (id in listOf("en", "ar_SA", "en@calendar=islamic", "en@calendar=islamic-civil", "en@calendar=islamic-tbla", "en@calendar=islamic-umalqura", "en@calendar=islamic-rgsa")) {
            val a = Source(SourceZone(0,"test"),SourceLocale(id)); val b = IslamicCalendar(SimpleTimeZone(0,"test"),ULocale(id))
            a.timeInMillis=1700000000000L; b.setTimeInMillis(1700000000000L); compare(a,b)
            for (civil in listOf(true,false,true)) { a.setCivil(civil); b.setCivil(civil); compare(a,b) }
            val ac=a.clone() as Source; val bc=b.clone() as IslamicCalendar
            ac.setRelatedYear(2025); bc.setRelatedYear(2025); compare(ac,bc); compare(a,b)
            assertFailsWith<NullPointerException> { a.setCalculationType(null) }
            assertFailsWith<NullPointerException> { b.setCalculationType(null) }
            compare(a,b)
        }
    }
    @Test fun fieldDateAndLocaleConstructors() {
        val pairs=listOf(
            Source(1445,8,1) to IslamicCalendar(1445,8,1),
            Source(1445,8,1,12,34,56) to IslamicCalendar(1445,8,1,12,34,56),
            Source(java.util.Date(0)) to IslamicCalendar(Date(0)))
        for ((a,b) in pairs) {
            for (f in 0 until a.fieldCount) assertEquals(a.isSet(f),b.isSet(f),"constructor stamp $f")
            compare(a,b)
        }
        for (id in listOf("en-u-ca-islamic-tbla", "ar-SA-u-ca-islamic-umalqura")) {
            val locale=java.util.Locale.forLanguageTag(id)
            val a=Source(locale); val b=IslamicCalendar(locale)
            a.timeInMillis=1700000000000L; b.setTimeInMillis(1700000000000L); compare(a,b)
        }
    }
    @Test fun earlyMetadataHasJavaZeroState() {
        val seen=mutableListOf<String>()
        val b=object:IslamicCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                assertFailsWith<NullPointerException> { getType() }
                assertFailsWith<NullPointerException> { isCivil() }
                assertEquals(0,getMinimum(ERA)); seen.add("early")
                setCalculationType(CalculationType.ISLAMIC_TBLA)
                return super.setWeekData(data)
            }
        }
        assertEquals(listOf("early"),seen)
        assertEquals(IslamicCalendar.CalculationType.ISLAMIC_CIVIL,b.getCalculationType())
    }
}
