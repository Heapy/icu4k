// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.javaCompatibleDoubleString
import io.github.heapy.icu4k.util.Date
import kotlin.math.*

/** ICU solar/lunar approximations and iterative event searches. Angles are in radians. */
open class CalendarAstronomer(aTime: Long) {
    constructor(): this(Date().getTime())
    private var time=aTime
    private var julianDay=INVALID
    private var sunLongitude=INVALID
    private var meanAnomalySun=INVALID
    private var moonEclipLong=INVALID
    private var moonPosition: Equatorial?=null

    open fun setTime(aTime: Long) { time=aTime; clearCache() }
    open fun setJulianDay(jdn: Double) { time=(jdn*DAY_MS).toLong()+JULIAN_EPOCH_MS; clearCache(); julianDay=jdn }
    open fun getTime(): Long=time
    open fun getDate(): Date=Date(time)
    open fun getJulianDay(): Double {
        if(julianDay==INVALID) julianDay=(time-JULIAN_EPOCH_MS).toDouble()/DAY_MS.toDouble()
        return julianDay
    }
    fun eclipticToEquatorial(eclipLong: Double,eclipLat: Double): Equatorial {
        val obliquity=eclipticObliquity()
        val sinE=sin(obliquity); val cosE=cos(obliquity)
        val sinL=sin(eclipLong); val cosL=cos(eclipLong)
        val sinB=sin(eclipLat); val cosB=cos(eclipLat); val tanB=tan(eclipLat)
        return Equatorial(atan2(sinL*cosE-tanB*sinE,cosL),asin(sinB*cosE+cosB*sinE*sinL))
    }
    open fun getSunLongitude(): Double {
        if(sunLongitude==INVALID) {
            val result=getSunLongitude(getJulianDay())
            if(result.isEmpty()) throw IndexOutOfBoundsException()
            sunLongitude=result[0]
            if(result.size<2) throw IndexOutOfBoundsException()
            meanAnomalySun=result[1]
        }
        return sunLongitude
    }
    internal open fun getSunLongitude(julian: Double): DoubleArray {
        val day=julian-JD_EPOCH
        val epochAngle=norm2PI(PI2/TROPICAL_YEAR*day)
        val anomaly=norm2PI(epochAngle+SUN_ETA_G-SUN_OMEGA_G)
        return doubleArrayOf(norm2PI(trueAnomaly(anomaly,SUN_E)+SUN_OMEGA_G),anomaly)
    }
    class SolarLongitude internal constructor(internal val value: Double)
    open fun getSunTime(desired: Double,next: Boolean): Long=timeOfAngle({ getSunLongitude() },desired,TROPICAL_YEAR,MINUTE_MS.toLong(),next)
    open fun getSunTime(desired: SolarLongitude?,next: Boolean): Long=getSunTime(desired!!.value,next)
    open fun getMoonPosition(): Equatorial {
        if(moonPosition==null) {
            val sunLong=getSunLongitude()
            val day=getJulianDay()-JD_EPOCH
            val meanLongitude=norm2PI(13.1763966*PI/180*day+MOON_L0)
            var anomaly=norm2PI(meanLongitude-0.1114041*PI/180*day-MOON_P0)
            val evection=1.2739*PI/180*sin(2*(meanLongitude-sunLong)-anomaly)
            val annual=0.1858*PI/180*sin(meanAnomalySun)
            val a3=0.3700*PI/180*sin(meanAnomalySun)
            anomaly+=evection-annual-a3
            val center=6.2886*PI/180*sin(anomaly)
            val a4=0.2140*PI/180*sin(2*anomaly)
            var longitude=meanLongitude+evection+center-annual+a4
            val variation=0.6583*PI/180*sin(2*(longitude-sunLong))
            longitude+=variation
            var node=norm2PI(MOON_N0-0.0529539*PI/180*day)
            node-=0.16*PI/180*sin(meanAnomalySun)
            val y=sin(longitude-node); val x=cos(longitude-node)
            moonEclipLong=atan2(y*cos(MOON_I),x)+node
            val latitude=asin(y*sin(MOON_I))
            moonPosition=eclipticToEquatorial(moonEclipLong,latitude)
        }
        return moonPosition!!
    }
    open fun getMoonAge(): Double { getMoonPosition(); return norm2PI(moonEclipLong-sunLongitude) }
    class MoonAge internal constructor(internal val value: Double)
    open fun getMoonTime(desired: Double,next: Boolean): Long=timeOfAngle({ getMoonAge() },desired,SYNODIC_MONTH,MINUTE_MS.toLong(),next)
    open fun getMoonTime(desired: MoonAge?,next: Boolean): Long=getMoonTime(desired!!.value,next)

    private fun timeOfAngle(function: ()->Double,desired: Double,periodDays: Double,epsilon: Long,next: Boolean): Long {
        var lastAngle=function()
        val deltaAngle=norm2PI(desired-lastAngle)
        var deltaT=(deltaAngle+if(next) 0.0 else -PI2)*(periodDays*DAY_MS)/PI2
        var lastDeltaT=deltaT
        val start=time
        setTime(time+deltaT.toLong())
        do {
            val angle=function()
            val factor=abs(deltaT/normPI(angle-lastAngle))
            deltaT=normPI(desired-angle)*factor
            if(abs(deltaT)>abs(lastDeltaT)) {
                val delta=(periodDays*DAY_MS/8).toLong()
                setTime(start+if(next) delta else -delta)
                return timeOfAngle(function,desired,periodDays,epsilon,next)
            }
            lastDeltaT=deltaT; lastAngle=angle
            setTime(time+deltaT.toLong())
        } while(abs(deltaT)>epsilon)
        return time
    }
    private fun trueAnomaly(mean: Double,eccentricity: Double): Double {
        var e=mean
        var delta: Double
        do {
            delta=e-eccentricity*sin(e)-mean
            e-=delta/(1-eccentricity*cos(e))
        } while(abs(delta)>1e-5)
        return 2.0*atan(tan(e/2)*sqrt((1+eccentricity)/(1-eccentricity)))
    }
    private fun eclipticObliquity(): Double {
        val t=(getJulianDay()-2451545.0)/36525
        val obliquity=23.439292-46.815/3600*t-0.0006/3600*t*t+0.00181/3600*t*t*t
        return obliquity*DEG_RAD
    }
    private fun clearCache() { julianDay=INVALID; sunLongitude=INVALID; meanAnomalySun=INVALID; moonEclipLong=INVALID; moonPosition=null }

    class Ecliptic(@kotlin.jvm.JvmField val latitude: Double,@kotlin.jvm.JvmField val longitude: Double) {
        override fun toString(): String=javaCompatibleDoubleString(longitude*RAD_DEG)+","+javaCompatibleDoubleString(latitude*RAD_DEG)
    }
    class Equatorial(@kotlin.jvm.JvmField val ascension: Double,@kotlin.jvm.JvmField val declination: Double) {
        override fun toString(): String=javaCompatibleDoubleString(ascension*RAD_DEG)+","+javaCompatibleDoubleString(declination*RAD_DEG)
        fun toHmsString(): String=radToHms(ascension)+","+radToDms(declination)
    }
    companion object {
        const val SIDEREAL_DAY=23.93446960027
        const val SOLAR_DAY=24.065709816
        const val SYNODIC_MONTH=29.530588853
        const val SIDEREAL_MONTH=27.32166
        const val TROPICAL_YEAR=365.242191
        const val SIDEREAL_YEAR=365.25636
        const val SECOND_MS=1000
        const val MINUTE_MS=60000
        const val HOUR_MS=3600000
        const val DAY_MS=86400000L
        const val JULIAN_EPOCH_MS=-210866760000000L
        internal const val EPOCH_2000_MS=946598400000L
        private const val PI=3.14159265358979323846
        private const val PI2=PI*2.0
        private const val DEG_RAD=PI/180
        private const val RAD_DEG=180/PI
        private const val RAD_HOUR=12/PI
        private const val JD_EPOCH=2447891.5
        private const val SUN_ETA_G=279.403303*PI/180
        private const val SUN_OMEGA_G=282.768422*PI/180
        private const val SUN_E=0.016713
        private const val MOON_L0=318.351648*PI/180
        private const val MOON_P0=36.340410*PI/180
        private const val MOON_N0=318.510107*PI/180
        private const val MOON_I=5.145366*PI/180
        private const val INVALID=Double.MIN_VALUE
        @kotlin.jvm.JvmField val WINTER_SOLSTICE=SolarLongitude(PI*3/2)
        @kotlin.jvm.JvmField val NEW_MOON=MoonAge(0.0)
        private fun normalize(value: Double,range: Double): Double=value-range*floor(value/range)
        private fun norm2PI(angle: Double): Double=normalize(angle,PI2)
        private fun normPI(angle: Double): Double=normalize(angle+PI,PI2)-PI
        private fun radToHms(angle: Double): String {
            val hours=(angle*RAD_HOUR).toInt()
            val minutes=((angle*RAD_HOUR-hours)*60).toInt()
            val seconds=((angle*RAD_HOUR-hours-minutes/60.0)*3600).toInt()
            return "${hours}h${minutes}m${seconds}s"
        }
        private fun radToDms(angle: Double): String {
            val degrees=(angle*RAD_DEG).toInt()
            val minutes=((angle*RAD_DEG-degrees)*60).toInt()
            val seconds=((angle*RAD_DEG-degrees-minutes/60.0)*3600).toInt()
            return "$degrees°$minutes'$seconds\""
        }
    }
}
