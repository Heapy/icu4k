package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CalendarAstronomer as Source
import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.*

class CalendarAstronomerOracleTest {
    private fun state(value: Any): List<Any?> = listOf("time","julianDay","sunLongitude","meanAnomalySun","moonEclipLong","moonPosition").map { name ->
        val field=value.javaClass.getDeclaredField(name).apply { isAccessible=true }
        when(val data=field.get(value)) {
            is Double -> data.toRawBits()
            is Source.Equatorial -> listOf(data.ascension.toRawBits(),data.declination.toRawBits())
            is CalendarAstronomer.Equatorial -> listOf(data.ascension.toRawBits(),data.declination.toRawBits())
            else -> data
        }
    }
    @Test fun positionsTimeMutationsAndCachesMatchBitForBit() {
        val a=Source(0); val b=CalendarAstronomer(0)
        val random=Random(622)
        for(time in listOf(Long.MIN_VALUE,Long.MAX_VALUE,0L,946728000000L,1710000000000L)+List(1000) { random.nextLong(-100000000000000L,100000000000000L) }) {
            a.setTime(time); b.setTime(time); assertEquals(state(a),state(b))
            assertEquals(a.julianDay.toRawBits(),b.getJulianDay().toRawBits())
            assertEquals(a.sunLongitude.toRawBits(),b.getSunLongitude().toRawBits())
            val x=a.moonPosition; val y=b.getMoonPosition()
            assertEquals(x.ascension.toRawBits(),y.ascension.toRawBits()); assertEquals(x.declination.toRawBits(),y.declination.toRawBits())
            assertSame(x,a.moonPosition); assertSame(y,b.getMoonPosition())
            assertEquals(a.moonAge.toRawBits(),b.getMoonAge().toRawBits()); assertEquals(state(a),state(b))
            assertEquals(a.date.time,b.getDate().getTime()); assertNotSame(b.getDate(),b.getDate())
        }
        for(day in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.0,0.0,Double.MIN_VALUE,2440587.5,2451545.0,1e20)) {
            a.setJulianDay(day); b.setJulianDay(day)
            assertEquals(a.time,b.getTime()); assertEquals(a.julianDay.toRawBits(),b.getJulianDay().toRawBits()); assertEquals(state(a),state(b))
        }
    }
    @Test fun eventSearchesAndCoordinateFormattingMatchSource() {
        val random=Random(2000)
        for(time in listOf(0L,648604800000L,946728000000L,1710000000000L)+List(100) { random.nextLong(-2000000000000L,3000000000000L) }) for(next in listOf(false,true)) {
            for(angle in listOf(0.0,PI/2,PI,PI*3/2)) {
                val a=Source(time); val b=CalendarAstronomer(time)
                assertEquals(a.getSunTime(angle,next),b.getSunTime(angle,next),"sun/$time/$angle/$next")
                assertEquals(state(a),state(b))
                a.setTime(time); b.setTime(time)
                assertEquals(a.getMoonTime(angle,next),b.getMoonTime(angle,next),"moon/$time/$angle/$next")
                assertEquals(state(a),state(b))
            }
        }
        for(angle in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.0,-PI,0.0,PI,1e30)) {
            val a=Source.Equatorial(angle,-angle); val b=CalendarAstronomer.Equatorial(angle,-angle)
            assertEquals(a.toString(),b.toString()); assertEquals(a.toHmsString(),b.toHmsString())
            assertEquals(Source.Ecliptic(angle,-angle).toString(),CalendarAstronomer.Ecliptic(angle,-angle).toString())
            val x=Source(0).eclipticToEquatorial(angle,angle); val y=CalendarAstronomer(0).eclipticToEquatorial(angle,angle)
            assertEquals(x.ascension.toRawBits(),y.ascension.toRawBits()); assertEquals(x.declination.toRawBits(),y.declination.toRawBits())
        }
    }
    @Test fun shortSolarArraysMatchSourcePartialCaches() {
        class Port(var values: DoubleArray) : CalendarAstronomer(0) {
            var calls=0
            override fun getSunLongitude(julian: Double): DoubleArray { calls++; return values }
        }
        for(values in listOf(doubleArrayOf(),doubleArrayOf(2.0))) {
            val a=com.ibm.icu.impl.AstronomerArrayOracle(values)
            val b=Port(values)
            assertFailsWith<IndexOutOfBoundsException> { a.sunLongitude }
            assertFailsWith<IndexOutOfBoundsException> { b.getSunLongitude() }
            a.values=doubleArrayOf(4.0,5.0); b.values=doubleArrayOf(4.0,5.0)
            assertEquals(a.sunLongitude,b.getSunLongitude()); assertEquals(a.calls,b.calls)
        }
    }

    @Test fun virtualCallbackSequenceAndPartialFailure() {
        class Original : Source(0) {
            val trace=ArrayList<Long>()
            var fail=false
            override fun setTime(time: Long) { trace.add(time); super.setTime(time); if(fail) error("setTime") }
        }
        class Port : CalendarAstronomer(0) {
            val trace=ArrayList<Long>()
            var fail=false
            override fun setTime(aTime: Long) { trace.add(aTime); super.setTime(aTime); if(fail) error("setTime") }
        }
        val a=Original(); val b=Port()
        assertEquals(a.getMoonTime(0.0,true),b.getMoonTime(CalendarAstronomer.NEW_MOON,true)); assertEquals(a.trace,b.trace)
        a.trace.clear(); b.trace.clear(); a.setTime(0); b.setTime(0)
        assertEquals(a.getSunTime(PI*3/2,true),b.getSunTime(CalendarAstronomer.WINTER_SOLSTICE,true)); assertEquals(a.trace,b.trace)
        a.fail=true; b.fail=true
        assertFailsWith<IllegalStateException> { a.getMoonTime(PI,false) }; assertFailsWith<IllegalStateException> { b.getMoonTime(PI,false) }
        assertEquals(a.time,b.getTime()); assertEquals(a.trace,b.trace)
    }
}
