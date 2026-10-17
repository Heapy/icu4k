@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.ChineseCalendar as Source
import com.ibm.icu.util.DangiCalendar as SourceDangi
import com.ibm.icu.util.SimpleTimeZone as SourceZone
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.test.*

class ChineseCalendarOracleTest {
    private fun compare(a: Source, b: ChineseCalendar) {
        assertEquals(a.timeInMillis,b.getTimeInMillis()); assertEquals(a.type,b.getType())
        for (f in 0 until a.fieldCount) assertEquals(a.get(f),b.get(f),"${a.type} field $f at ${a.timeInMillis}")
        assertEquals(a.temporalMonthCode,b.getTemporalMonthCode())
        assertEquals(a.inTemporalLeapYear(),b.inTemporalLeapYear())
        for (f in listOf(Calendar.DATE,Calendar.DAY_OF_YEAR,Calendar.ORDINAL_MONTH)) assertEquals(a.getActualMaximum(f),b.getActualMaximum(f),"actual $f")
    }
    @Test fun historicalAndModernFieldsMatch() {
        for (dangi in listOf(false,true)) for (offset in listOf(0,19800000)) {
            val a=if(dangi) SourceDangi(SourceZone(offset,"test"),SourceLocale.ROOT) else Source(SourceZone(offset,"test"),SourceLocale.ROOT)
            val b=if(dangi) DangiCalendar(SimpleTimeZone(offset,"test"),ULocale.ROOT) else ChineseCalendar(SimpleTimeZone(offset,"test"),ULocale.ROOT)
            for (year in listOf(1896,1897,1898,1911,1912,1913,2000,2023,2024)) for (month in 1..12) for (day in listOf(1,15,28)) {
                val time=java.time.LocalDate.of(year,month,day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                a.timeInMillis=time; b.setTimeInMillis(time); compare(a,b)
            }
            for(f in 0 until a.fieldCount) {
                assertEquals(a.getMinimum(f),b.getMinimum(f));assertEquals(a.getMaximum(f),b.getMaximum(f))
                assertEquals(a.getGreatestMinimum(f),b.getGreatestMinimum(f));assertEquals(a.getLeastMaximum(f),b.getLeastMaximum(f))
            }
        }
    }
    @Test fun leapMonthArithmeticAndClones() {
        for (dangi in listOf(false,true)) {
            val a=if(dangi) SourceDangi(SourceZone(0,"test"),SourceLocale.ROOT) else Source(SourceZone(0,"test"),SourceLocale.ROOT)
            val b=if(dangi) DangiCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT) else ChineseCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
            for (time in listOf(1679356800000L,1680220800000L,-2288563200000L)) {
                a.timeInMillis=time;b.setTimeInMillis(time)
                for (field in listOf(Calendar.MONTH,Calendar.ORDINAL_MONTH,Calendar.YEAR,Calendar.DATE)) for(amount in listOf(-13,-1,1,13)) {
                    val ac=a.clone() as Source;val bc=b.clone() as ChineseCalendar
                    ac.add(field,amount);bc.add(field,amount);compare(ac,bc)
                    ac.roll(field,-amount);bc.roll(field,-amount);compare(ac,bc)
                }
                compare(a,b)
                for (code in listOf("M01","M02L","M12L")) {
                    val ac=a.clone() as Source;val bc=b.clone() as ChineseCalendar
                    ac.temporalMonthCode=code;bc.setTemporalMonthCode(code); compare(ac,bc)
                }
            }
        }
    }
    @Test fun constructorFieldsAndOrdinalResolution() {
        val pairs=listOf(
            Source(40,1,0,15) to ChineseCalendar(40,1,0,15),
            Source(78,40,1,1,15) to ChineseCalendar(78,40,1,1,15),
            Source(78,40,1,1,15,12,34,56) to ChineseCalendar(78,40,1,1,15,12,34,56),
            Source(java.util.Date(0)) to ChineseCalendar(Date(0)),
            SourceDangi(java.util.Date(0)) to DangiCalendar(Date(0)))
        for((a,b) in pairs) {
            for(f in 0 until a.fieldCount) assertEquals(a.isSet(f),b.isSet(f),"constructor field $f")
            compare(a,b)
        }
        val a=Source(SourceZone(0,"test"),SourceLocale.ROOT);val b=ChineseCalendar(SimpleTimeZone(0,"test"),ULocale.ROOT)
        for(ordinal in 0..12) {
            a.clear();b.clear();a.set(Calendar.EXTENDED_YEAR,2023);b.set(Calendar.EXTENDED_YEAR,2023)
            a.set(Calendar.ORDINAL_MONTH,ordinal);b.set(Calendar.ORDINAL_MONTH,ordinal)
            a.set(Calendar.DATE,30);b.set(Calendar.DATE,30);compare(a,b)
        }
    }
    @Test fun customAstronomicalZonesAndEarlyCallbacks() {
        val sourceEvents=mutableListOf<String>();val portEvents=mutableListOf<String>()
        val sourceAstro=SourceZone(3600000,"astro");val portAstro=SimpleTimeZone(3600000,"astro")
        val a=object:Source(SourceZone(0,"display"),SourceLocale.ROOT,sourceAstro) {
            override fun setWeekData(data: com.ibm.icu.util.Calendar.WeekData?): com.ibm.icu.util.Calendar {
                sourceEvents.add("$type:${getMinimum(ERA)}:${getMaximum(YEAR)}")
                return super.setWeekData(data)
            }
            override fun setTimeInMillis(value: Long) { sourceEvents.add("clock");super.setTimeInMillis(value) }
        }
        val b=object:ChineseCalendar(SimpleTimeZone(0,"display"),ULocale.ROOT,portAstro) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                portEvents.add("${getType()}:${getMinimum(ERA)}:${getMaximum(YEAR)}")
                return super.setWeekData(data)
            }
            override fun setTimeInMillis(value: Long) { portEvents.add("clock");super.setTimeInMillis(value) }
        }
        assertEquals(sourceEvents,portEvents)
        for(time in listOf(-2288563200000L,1679443200000L)) { a.timeInMillis=time;b.setTimeInMillis(time);compare(a,b) }
        val ac=a.clone() as Source;val bc=b.clone() as ChineseCalendar
        sourceAstro.rawOffset=7200000;portAstro.setRawOffset(7200000)
        ac.timeInMillis=1930435200000L;bc.setTimeInMillis(1930435200000L);compare(ac,bc)
        a.timeInMillis=1930435200000L;b.setTimeInMillis(1930435200000L);compare(a,b)
    }

}
