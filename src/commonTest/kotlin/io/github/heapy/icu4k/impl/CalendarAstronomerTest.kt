package io.github.heapy.icu4k.impl

import kotlin.math.abs
import kotlin.test.*

class CalendarAstronomerTest {
    @Test fun sourceSolarLunarSnapshotsAcrossEpochs() {
        val data=listOf(
            doubleArrayOf(0.0,2440587.5,4.889514678257451,4.723958718633174,-2.983432933918617,-0.11035734843816977),
            doubleArrayOf(946728000000.0,2451545.0,4.893682227533986,5.28401126823327,-2.4041938430198613,-0.19059493679904424),
            doubleArrayOf(1710000000000.0,2460379.1666666665,6.101510804584955,6.105413156068077,-0.3109552569354315,-0.19128984382281564),
        )
        for(row in data) {
            val a=CalendarAstronomer(row[0].toLong())
            assertEquals(row[1],a.getJulianDay())
            assertEquals(row[2],a.getSunLongitude(),1e-12)
            assertEquals(row[3],a.getMoonAge(),1e-12)
            val p=a.getMoonPosition()
            assertEquals(row[4],p.ascension,1e-12); assertEquals(row[5],p.declination,1e-12)
            assertSame(p,a.getMoonPosition())
            a.setTime(a.getTime()); assertNotSame(p,a.getMoonPosition())
        }
    }
    @Test fun searchesMatchSourceAndMutateStoredTime() {
        val rows=listOf(
            longArrayOf(0,30696397877,592909218),
            longArrayOf(946728000000,977404156949,947182762092),
            longArrayOf(1710000000000,1734770364214,1710061672523),
        )
        for(row in rows) {
            val a=CalendarAstronomer(row[0])
            val sun=a.getSunTime(CalendarAstronomer.WINTER_SOLSTICE,true)
            assertTrue(abs(sun-row[1])<=10,"sun $sun vs ${row[1]}"); assertEquals(sun,a.getTime())
            a.setTime(row[0]); val moon=a.getMoonTime(CalendarAstronomer.NEW_MOON,true)
            assertTrue(abs(moon-row[2])<=10,"moon $moon vs ${row[2]}"); assertEquals(moon,a.getTime())
            a.setTime(row[0]); assertTrue(a.getMoonTime(0.0,false)<row[0])
        }
    }
    @Test fun julianSentinelOverflowNaNAndFreshDates() {
        val a=CalendarAstronomer(0)
        val date=a.getDate(); date.setTime(42)
        assertEquals(0L,a.getTime()); assertNotSame(date,a.getDate())
        a.setJulianDay(Double.MIN_VALUE); assertEquals(CalendarAstronomer.JULIAN_EPOCH_MS,a.getTime()); assertEquals(0.0,a.getJulianDay())
        a.setJulianDay(Double.NaN); assertEquals(CalendarAstronomer.JULIAN_EPOCH_MS,a.getTime()); assertTrue(a.getJulianDay().isNaN())
        a.setJulianDay(Double.POSITIVE_INFINITY)
        assertEquals(Long.MAX_VALUE+CalendarAstronomer.JULIAN_EPOCH_MS,a.getTime()); assertEquals(Double.POSITIVE_INFINITY,a.getJulianDay())
        a.setTime(Long.MAX_VALUE)
        val wrapped=Long.MAX_VALUE-CalendarAstronomer.JULIAN_EPOCH_MS
        assertEquals(wrapped.toDouble()/CalendarAstronomer.DAY_MS,a.getJulianDay())
        assertEquals("0.0,0.0",CalendarAstronomer.Ecliptic(0.0,0.0).toString())
        assertEquals("0h0m0s,0°0'0\"",CalendarAstronomer.Equatorial(0.0,0.0).toHmsString())
    }
    @Test fun shortSolarArraysRetainPartialCacheAndFailureTiming() {
        class Script(var values: DoubleArray) : CalendarAstronomer(0) {
            var calls=0
            override fun getSunLongitude(julian: Double): DoubleArray { calls++; return values }
        }
        val empty=Script(doubleArrayOf())
        assertFailsWith<IndexOutOfBoundsException> { empty.getSunLongitude() }
        empty.values=doubleArrayOf(2.0,3.0); assertEquals(2.0,empty.getSunLongitude()); assertEquals(2,empty.calls)
        val partial=Script(doubleArrayOf(2.0))
        assertFailsWith<IndexOutOfBoundsException> { partial.getSunLongitude() }
        assertEquals(2.0,partial.getSunLongitude()); assertEquals(1,partial.calls)
    }

    @Test fun virtualDispatchNaNSearchAndPartialSetterFailure() {
        class Script : CalendarAstronomer(0) {
            val trace=ArrayList<String>()
            var fail=false
            override fun getSunLongitude(): Double { trace.add("sun"); return 1.0 }
            override fun setTime(aTime: Long) { trace.add("time:$aTime"); super.setTime(aTime); if(fail) error("set") }
            override fun getTime(): Long=999
        }
        val a=Script()
        assertEquals(0L,a.getSunTime(Double.NaN,true))
        assertEquals(listOf("sun","time:0","sun","time:0"),a.trace)
        assertEquals(0L,a.getDate().getTime())
        a.trace.clear(); a.fail=true
        assertFailsWith<IllegalStateException> { a.getSunTime(2.0,true) }
        assertEquals(2,a.trace.size); assertTrue(a.getDate().getTime()!=0L)
        assertFailsWith<NullPointerException> { CalendarAstronomer(0).getMoonTime(null,false) }
        assertFailsWith<NullPointerException> { CalendarAstronomer(0).getSunTime(null,true) }
    }
}
