// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalendarAstronomer
import io.github.heapy.icu4k.impl.CalendarCache
import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.util.ULocale
import kotlin.math.*

/** IslamicCalendar's four calculation modes, field conversion and mode transitions. */
internal class IslamicCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun internalGetTimeInMillis(): Long
        fun getTimeInMillis(): Long
        fun setTimeInMillis(value: Long)
        fun clear()
        fun get(field: Int): Int
        fun set(field: Int, value: Int)
        fun getActualMaximum(field: Int): Int
    }
    enum class CalculationType(val bcpType: String) {
        ISLAMIC("islamic"), ISLAMIC_CIVIL("islamic-civil"),
        ISLAMIC_UMALQURA("islamic-umalqura"), ISLAMIC_TBLA("islamic-tbla")
    }
    private var cType: CalculationType? = CalculationType.ISLAMIC_CIVIL
    private var algorithm: Algorithm? = CIVIL
    private var civil = true
    fun restoreSerializedFields(type: CalculationType, legacyCivil: Boolean) { cType = type; civil = legacyCivil }
    fun serializedCivil(): Boolean = civil
    fun serializedType(): CalculationType? = cType
    fun initializeDefaults() { cType = CalculationType.ISLAMIC_CIVIL; algorithm = CIVIL; civil = true }
    fun copyForOwner(newOwner: Owner): IslamicCalendarCore = IslamicCalendarCore(newOwner).also {
        it.cType = cType; it.algorithm = algorithm; it.civil = civil
    }
    fun setCalculationType(type: CalculationType?) {
        // The source assigns the serialized type before the null-failing switch.
        cType = type
        algorithm = when (type!!) {
            CalculationType.ISLAMIC -> ISLAMIC
            CalculationType.ISLAMIC_CIVIL -> CIVIL
            CalculationType.ISLAMIC_TBLA -> TBLA
            CalculationType.ISLAMIC_UMALQURA -> UMALQURA
        }
        civil = algorithm!!.isCivil()
    }
    fun setCivil(beCivil: Boolean) {
        if (beCivil && cType != CalculationType.ISLAMIC_CIVIL) {
            val time = owner.getTimeInMillis()
            cType = CalculationType.ISLAMIC_CIVIL
            algorithm = CIVIL
            owner.clear()
            owner.setTimeInMillis(time)
        } else if (!beCivil && cType != CalculationType.ISLAMIC) {
            val time = owner.getTimeInMillis()
            cType = CalculationType.ISLAMIC
            algorithm = ISLAMIC
            owner.clear()
            owner.setTimeInMillis(time)
        }
        civil = algorithm!!.isCivil()
    }
    fun isCivil(): Boolean = algorithm!!.isCivil()
    fun getCalculationType(): CalculationType = algorithm!!.type
    fun getType(): String = algorithm!!.type.bcpType
    fun setCalcTypeForLocale(locale: ULocale) {
        val type = CalendarUtil.getCalendarType(locale)
        setCalculationType(when {
            type == "islamic-civil" -> CalculationType.ISLAMIC_CIVIL
            type == "islamic-umalqura" -> CalculationType.ISLAMIC_UMALQURA
            type == "islamic-tbla" -> CalculationType.ISLAMIC_TBLA
            type.startsWith("islamic") -> CalculationType.ISLAMIC
            else -> CalculationType.ISLAMIC_CIVIL
        })
    }
    fun handleGetLimit(field: Int, type: Int): Int {
        if (field !in LIMITS.indices || type !in LIMITS[field].indices) throw IndexOutOfBoundsException()
        return LIMITS[field][type]
    }
    fun handleGetExtendedYear(): Int = if (owner.newerField(19, 1) == 19) owner.internalGet(19, 1) else owner.internalGet(1, 1)
    fun handleGetMonthLength(year: Int, month: Int): Int = algorithm!!.monthLength(year, month)
    fun handleGetYearLength(year: Int): Int = algorithm!!.yearLength(year)
    @Suppress("UNUSED_PARAMETER")
    fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int = (algorithm!!.monthStart(year, month) + algorithm!!.epoch() - 1).toInt()
    fun handleComputeFields(julianDay: Int) {
        algorithm!!.compute(julianDay.toLong(), owner.internalGetTimeInMillis(),
            { year -> owner.internalSet(0, 0); owner.internalSet(1, year); owner.internalSet(19, year) },
            { month -> owner.internalSet(2, month); owner.internalSet(23, month) },
            { owner.internalSet(5, it) }, { owner.internalSet(6, it) })
    }
    fun getRelatedYear(): Int = gregoYearFromIslamicStart(owner.get(19))
    fun setRelatedYear(year: Int) = owner.set(19, firstIslamicStartYearFromGrego(year))
    fun inTemporalLeapYear(): Boolean = owner.getActualMaximum(6) == 355

    private abstract class Algorithm(val type: CalculationType) {
        open fun isCivil(): Boolean = false
        open fun epoch(): Long = 1948440L
        abstract fun yearStart(year: Int): Long
        abstract fun monthStart(year: Int, month: Int): Long
        abstract fun monthLength(year: Int, month: Int): Int
        abstract fun yearLength(year: Int): Int
        abstract fun compute(julian: Long, current: Long, year: (Int)->Unit, month: (Int)->Unit, day: (Int)->Unit, dayOfYear: (Int)->Unit)
        fun emit(days: Long, y: Int, m: Int, year: (Int)->Unit, month: (Int)->Unit, day: (Int)->Unit, dayOfYear: (Int)->Unit) {
            year(y); month(m)
            day((days - monthStart(y, m)).toInt() + 1)
            dayOfYear((days - yearStart(y) + 1).toInt())
        }
    }
    private open class CivilAlgorithm(type: CalculationType = CalculationType.ISLAMIC_CIVIL): Algorithm(type) {
        override fun isCivil(): Boolean = true
        override fun yearStart(year: Int): Long = (year - 1) * 354 + floor((3 + 11 * year) / 30.0).toLong()
        override fun monthStart(year: Int, month: Int): Long = ceil(29.5 * (month % 12)).toLong() + yearStart(year + month / 12)
        override fun monthLength(year: Int, month: Int): Int = 29 + (if (month % 2 == 0) 1 else 0) + (if (month == 11 && civilLeapYear(year)) 1 else 0)
        override fun yearLength(year: Int): Int = 354 + if (civilLeapYear(year)) 1 else 0
        override fun compute(julian: Long, current: Long, year: (Int)->Unit, month: (Int)->Unit, day: (Int)->Unit, dayOfYear: (Int)->Unit) {
            val days = julian - epoch()
            val y = floor((30 * days + 10646) / 10631.0).toInt()
            val m = min(ceil((days - 29 - yearStart(y)) / 29.5).toInt(), 11)
            emit(days, y, m, year, month, day, dayOfYear)
        }
    }
    private class IslamicAlgorithm: Algorithm(CalculationType.ISLAMIC) {
        override fun yearStart(year: Int): Long = monthStart(year, 0)
        override fun monthStart(year: Int, month: Int): Long = trueMonthStart((12 * ((year + month / 12) - 1) + month % 12).toLong())
        override fun monthLength(year: Int, month: Int): Int {
            val m = month + 12 * (year - 1)
            return (trueMonthStart((m + 1).toLong()) - trueMonthStart(m.toLong())).toInt()
        }
        override fun yearLength(year: Int): Int {
            val m = 12 * (year - 1)
            return (trueMonthStart((m + 12).toLong()) - trueMonthStart(m.toLong())).toInt()
        }
        override fun compute(julian: Long, current: Long, year: (Int)->Unit, month: (Int)->Unit, day: (Int)->Unit, dayOfYear: (Int)->Unit) {
            val days = julian - epoch()
            var m = floor(days / CalendarAstronomer.SYNODIC_MONTH).toInt()
            val estimate = floor(m * CalendarAstronomer.SYNODIC_MONTH - 1).toLong()
            if (days - estimate >= 25 && moonAge(current) > 0) m++
            while (trueMonthStart(m.toLong()) > days) m--
            val y = if (m >= 0) m / 12 + 1 else (m + 1) / 12
            m = ((m % 12) + 12) % 12
            emit(days, y, m, year, month, day, dayOfYear)
        }
    }
    private class UmalquraAlgorithm: Algorithm(CalculationType.ISLAMIC_UMALQURA) {
        override fun yearStart(year: Int): Long {
            if (year !in 1300..1600) return CIVIL.yearStart(year)
            val index = year - 1300
            val estimate = (354.36720 * index + 460322.05 + 0.5).toInt()
            return (estimate + UMALQURA_YEAR_START_ESTIMATE_FIX[index]).toLong()
        }
        override fun monthStart(year: Int, month: Int): Long {
            val y = year + month / 12
            val m = month % 12
            if (y < 1300) return CIVIL.monthStart(y, m)
            var result = yearStart(y)
            for (i in 0 until m) result += monthLength(y, i)
            return result
        }
        override fun monthLength(year: Int, month: Int): Int {
            if (year !in 1300..1600) return CIVIL.monthLength(year, month)
            return if ((UMALQURA_MONTHLENGTH[year - 1300] and (1 shl (11 - month))) != 0) 30 else 29
        }
        override fun yearLength(year: Int): Int = if (year !in 1300..1600) CIVIL.yearLength(year) else (0..11).sumOf { monthLength(year, it) }
        override fun compute(julian: Long, current: Long, year: (Int)->Unit, month: (Int)->Unit, day: (Int)->Unit, dayOfYear: (Int)->Unit) {
            val days = julian - epoch()
            if (days < yearStart(1300)) { CIVIL.compute(julian, current, year, month, day, dayOfYear); return }
            var y = ((days - (460322.05 + 0.5)) / 354.36720).toInt() + 1300 - 1
            var m = 0
            var d = 1L
            while (d > 0) {
                y++
                d = days - yearStart(y) + 1
                val length = yearLength(y)
                if (d == length.toLong()) { m = 11; break }
                else if (d < length) {
                    var monthLen = monthLength(y, m)
                    m = 0
                    while (d > monthLen) { d -= monthLen; monthLen = monthLength(y, ++m) }
                    break
                }
            }
            emit(days, y, m, year, month, day, dayOfYear)
        }
    }
    companion object {
        fun beforeFieldInitialization(owner: Owner): IslamicCalendarCore = IslamicCalendarCore(owner).also {
            it.cType = null; it.algorithm = null; it.civil = false
        }

        private const val HIJRA_MILLIS = -42521587200000L
        private const val ONE_DAY = 86400000L
        private val ISLAMIC: Algorithm = IslamicAlgorithm()
        private val CIVIL: Algorithm = CivilAlgorithm()
        private val TBLA: Algorithm = object: CivilAlgorithm(CalculationType.ISLAMIC_TBLA) {
            override fun isCivil(): Boolean = false
            override fun epoch(): Long = 1948439L
        }
        private val UMALQURA: Algorithm = UmalquraAlgorithm()
        private var cache = CalendarCache()
        fun civilLeapYear(year: Int): Boolean = (14 + 11 * year) % 30 < 11
        fun moonAge(time: Long): Double {
            var age = CalendarAstronomer(time).getMoonAge() * 180 / PI
            if (age > 180) age -= 360
            return age
        }
        fun trueMonthStart(month: Long): Long {
            var start = cache.get(month)
            if (start == CalendarCache.EMPTY) {
                var origin = HIJRA_MILLIS + floor(month * CalendarAstronomer.SYNODIC_MONTH).toLong() * ONE_DAY
                var age = moonAge(origin)
                if (moonAge(origin) >= 0) {
                    do { origin -= ONE_DAY; age = moonAge(origin) } while (age >= 0)
                } else {
                    do { origin += ONE_DAY; age = moonAge(origin) } while (age < 0)
                }
                start = (origin - HIJRA_MILLIS) / ONE_DAY + 1
                cache.put(month, start)
            }
            return start
        }
        fun gregoYearFromIslamicStart(year: Int): Int {
            val cycle: Int; val offset: Int
            val shift: Int
            if (year >= 1397) { cycle = (year - 1397) / 67; offset = (year - 1397) % 67; shift = 2 * cycle + if (offset >= 33) 1 else 0 }
            else { cycle = (year - 1396) / 67 - 1; offset = -(year - 1396) % 67; shift = 2 * cycle + if (offset <= 33) 1 else 0 }
            return year + 579 - shift
        }
        fun firstIslamicStartYearFromGrego(year: Int): Int {
            val cycle: Int; val offset: Int
            val shift: Int
            if (year >= 1977) { cycle = (year - 1977) / 65; offset = (year - 1977) % 65; shift = 2 * cycle + if (offset >= 32) 1 else 0 }
            else { cycle = (year - 1976) / 65 - 1; offset = -(year - 1976) % 65; shift = 2 * cycle + if (offset <= 32) 1 else 0 }
            return year - 579 + shift
        }
        private val LIMITS = arrayOf(
            intArrayOf(0,0,0,0), intArrayOf(1,1,5000000,5000000), intArrayOf(0,0,11,11), intArrayOf(1,1,50,51),
            intArrayOf(), intArrayOf(1,1,29,30), intArrayOf(1,1,354,355), intArrayOf(), intArrayOf(-1,-1,5,5),
            intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(),
            intArrayOf(1,1,5000000,5000000), intArrayOf(), intArrayOf(1,1,5000000,5000000), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0,0,11,11),
        )
        private val UMALQURA_MONTHLENGTH = intArrayOf(
            0x0AAA,0x0D54,0x0EC9,0x06D4,0x06EA,0x036C,0x0AAD,0x0555,0x06A9,0x0792,0x0BA9,0x05D4,
            0x0ADA,0x055C,0x0D2D,0x0695,0x074A,0x0B54,0x0B6A,0x05AD,0x04AE,0x0A4F,0x0517,0x068B,
            0x06A5,0x0AD5,0x02D6,0x095B,0x049D,0x0A4D,0x0D26,0x0D95,0x05AC,0x09B6,0x02BA,0x0A5B,
            0x052B,0x0A95,0x06CA,0x0AE9,0x02F4,0x0976,0x02B6,0x0956,0x0ACA,0x0BA4,0x0BD2,0x05D9,
            0x02DC,0x096D,0x054D,0x0AA5,0x0B52,0x0BA5,0x05B4,0x09B6,0x0557,0x0297,0x054B,0x06A3,
            0x0752,0x0B65,0x056A,0x0AAB,0x052B,0x0C95,0x0D4A,0x0DA5,0x05CA,0x0AD6,0x0957,0x04AB,
            0x094B,0x0AA5,0x0B52,0x0B6A,0x0575,0x0276,0x08B7,0x045B,0x0555,0x05A9,0x05B4,0x09DA,
            0x04DD,0x026E,0x0936,0x0AAA,0x0D54,0x0DB2,0x05D5,0x02DA,0x095B,0x04AB,0x0A55,0x0B49,
            0x0B64,0x0B71,0x05B4,0x0AB5,0x0A55,0x0D25,0x0E92,0x0EC9,0x06D4,0x0AE9,0x096B,0x04AB,
            0x0A93,0x0D49,0x0DA4,0x0DB2,0x0AB9,0x04BA,0x0A5B,0x052B,0x0A95,0x0B2A,0x0B55,0x055C,
            0x04BD,0x023D,0x091D,0x0A95,0x0B4A,0x0B5A,0x056D,0x02B6,0x093B,0x049B,0x0655,0x06A9,
            0x0754,0x0B6A,0x056C,0x0AAD,0x0555,0x0B29,0x0B92,0x0BA9,0x05D4,0x0ADA,0x055A,0x0AAB,
            0x0595,0x0749,0x0764,0x0BAA,0x05B5,0x02B6,0x0A56,0x0E4D,0x0B25,0x0B52,0x0B6A,0x05AD,
            0x02AE,0x092F,0x0497,0x064B,0x06A5,0x06AC,0x0AD6,0x055D,0x049D,0x0A4D,0x0D16,0x0D95,
            0x05AA,0x05B5,0x02DA,0x095B,0x04AD,0x0595,0x06CA,0x06E4,0x0AEA,0x04F5,0x02B6,0x0956,
            0x0AAA,0x0B54,0x0BD2,0x05D9,0x02EA,0x096D,0x04AD,0x0A95,0x0B4A,0x0BA5,0x05B2,0x09B5,
            0x04D6,0x0A97,0x0547,0x0693,0x0749,0x0B55,0x056A,0x0A6B,0x052B,0x0A8B,0x0D46,0x0DA3,
            0x05CA,0x0AD6,0x04DB,0x026B,0x094B,0x0AA5,0x0B52,0x0B69,0x0575,0x0176,0x08B7,0x025B,
            0x052B,0x0565,0x05B4,0x09DA,0x04ED,0x016D,0x08B6,0x0AA6,0x0D52,0x0DA9,0x05D4,0x0ADA,
            0x095B,0x04AB,0x0653,0x0729,0x0762,0x0BA9,0x05B2,0x0AB5,0x0555,0x0B25,0x0D92,0x0EC9,
            0x06D2,0x0AE9,0x056B,0x04AB,0x0A55,0x0D29,0x0D54,0x0DAA,0x09B5,0x04BA,0x0A3B,0x049B,
            0x0A4D,0x0AAA,0x0AD5,0x02DA,0x095D,0x045E,0x0A2E,0x0C9A,0x0D55,0x06B2,0x06B9,0x04BA,
            0x0A5D,0x052D,0x0A95,0x0B52,0x0BA8,0x0BB4,0x05B9,0x02DA,0x095A,0x0B4A,0x0DA4,0x0ED1,
            0x06E8,0x0B6A,0x056D,0x0535,0x0695,0x0D4A,0x0DA8,0x0DD4,0x06DA,0x055B,0x029D,0x062B,
            0x0B15,0x0B4A,0x0B95,0x05AA,0x0AAE,0x092E,0x0C8F,0x0527,0x0695,0x06AA,0x0AD6,0x055D,
            0x029D,
        )
        private val UMALQURA_YEAR_START_ESTIMATE_FIX = intArrayOf(
            0,0,-1,0,-1,0,0,0,0,0,-1,0,
            0,0,0,0,0,0,-1,0,1,0,1,1,
            0,0,0,0,1,0,0,0,0,0,0,0,
            1,0,0,0,0,0,1,0,0,-1,-1,0,
            0,0,1,0,0,-1,0,0,0,1,1,0,
            0,0,0,0,0,0,0,-1,0,0,0,1,
            1,0,0,-1,0,1,0,1,1,0,0,-1,
            0,1,0,0,0,-1,0,1,0,1,0,0,
            0,-1,0,0,0,0,-1,-1,0,-1,0,1,
            0,0,0,-1,0,0,0,1,0,0,0,0,
            0,1,0,0,-1,-1,0,0,0,1,0,0,
            -1,-1,0,-1,0,0,-1,-1,0,-1,0,-1,
            0,0,-1,-1,0,0,0,0,0,0,-1,0,
            1,0,1,1,0,0,-1,0,1,0,0,0,
            0,0,1,0,1,0,0,0,-1,0,1,0,
            0,-1,-1,0,0,0,1,0,0,0,0,0,
            0,0,1,0,0,0,0,0,1,0,0,-1,
            0,0,0,1,1,0,0,-1,0,1,0,1,
            1,0,0,0,0,1,0,0,0,-1,0,0,
            0,1,0,0,0,-1,0,0,0,0,0,-1,
            0,-1,0,1,0,0,0,-1,0,1,0,1,
            0,0,0,0,0,1,0,0,-1,0,0,0,
            0,1,0,0,0,-1,0,0,0,0,-1,-1,
            0,-1,0,1,0,0,-1,-1,0,0,1,1,
            0,0,-1,0,0,0,0,1,0,0,0,0,
            1,
        )
    }
}
