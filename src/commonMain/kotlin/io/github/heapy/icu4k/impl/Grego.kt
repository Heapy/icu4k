// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Proleptic Gregorian arithmetic with ICU4J's integer overflow behavior. */
object Grego {
    const val MIN_MILLIS = -184303902528000000L
    const val MAX_MILLIS = 183882168921600000L
    const val MILLIS_PER_SECOND = 1000
    const val MILLIS_PER_MINUTE = 60000
    const val MILLIS_PER_HOUR = 3600000
    const val MILLIS_PER_DAY = 86400000
    private const val JULIAN_1_CE = 1721426
    private const val JULIAN_1970_CE = 2440588
    private val monthLengths = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31,31,29,31,30,31,30,31,31,30,31,30,31)
    private val daysBefore = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334,0,31,60,91,121,152,182,213,244,274,305,335)
    fun isLeapYear(year: Int): Boolean = year and 3 == 0 && (year % 100 != 0 || year % 400 == 0)
    fun monthLength(year: Int, month: Int): Int = checkedArrayElement(monthLengths, month + if (isLeapYear(year)) 12 else 0)
    fun previousMonthLength(year: Int, month: Int): Int = if (month > 0) monthLength(year, month - 1) else 31
    fun fieldsToDay(year: Int, month: Int, dom: Int): Long {
        val y = year - 1
        val julian = (365 * y).toLong() + floorDivide(y.toLong(), 4) + (JULIAN_1_CE - 3) + floorDivide(y.toLong(), 400) - floorDivide(y.toLong(), 100) + 2 + checkedArrayElement(daysBefore, month + if (isLeapYear(year)) 12 else 0) + dom
        return julian - JULIAN_1970_CE
    }
    fun floorDivide(numerator: Long, denominator: Long): Long = if (numerator >= 0) numerator / denominator else (numerator + 1) / denominator - 1
    private fun divide(numerator: Long, denominator: Int): Pair<Long, Int> {
        val quotient = floorDivide(numerator, denominator.toLong())
        return quotient to (if (numerator >= 0) numerator % denominator else numerator - quotient * denominator).toInt()
    }
    fun dayOfWeek(day: Long): Int = divide(day + 5, 7).second.let { if (it == 0) 7 else it }
    fun dayToYear(day: Long): Pair<Int, Int> {
        val n400 = divide(day + JULIAN_1970_CE - JULIAN_1_CE, 146097)
        val n100 = divide(n400.second.toLong(), 36524)
        val n4 = divide(n100.second.toLong(), 1461)
        val n1 = divide(n4.second.toLong(), 365)
        var year = (400 * n400.first + 100 * n100.first + 4 * n4.first + n1.first).toInt()
        val doy = if (n100.first == 4L || n1.first == 4L) 365 else { year++; n1.second }
        return year to doy + 1
    }
    fun dayToFields(day: Long, fields: IntArray?): IntArray {
        val result = if (fields == null || fields.size < 5) IntArray(5) else fields
        val (year, doy) = dayToYear(day)
        val leap = isLeapYear(year)
        val correction = if (doy > if (leap) 60 else 59) if (leap) 1 else 2 else 0
        val month = (12 * (doy - 1 + correction) + 6) / 367
        val dom = doy - checkedArrayElement(daysBefore, month + if (leap) 12 else 0)
        var dow = ((day + JULIAN_1970_CE - JULIAN_1_CE + 2) % 7).toInt()
        if (dow < 1) dow += 7
        result[0] = year; result[1] = month; result[2] = dom; result[3] = dow; result[4] = doy
        return result
    }
    fun timeToFields(time: Long, fields: IntArray?): IntArray {
        val result = if (fields == null || fields.size < 6) IntArray(6) else fields
        val (day, millis) = divide(time, MILLIS_PER_DAY)
        dayToFields(day, result); result[5] = millis
        return result
    }
    fun timeToYear(time: Long): Int = dayToYear(divide(time, MILLIS_PER_DAY).first).first
    fun getDayOfWeekInMonth(year: Int, month: Int, dom: Int): Int {
        val week = (dom + 6) / 7
        return if (week == 5 || week == 4 && dom + 7 > monthLength(year, month)) -1 else week
    }
    fun timeToString(time: Long): String {
        val fields = timeToFields(time, null)
        var millis = fields[5]
        val hour = millis / MILLIS_PER_HOUR; millis %= MILLIS_PER_HOUR
        val minute = millis / MILLIS_PER_MINUTE; millis %= MILLIS_PER_MINUTE
        val second = millis / MILLIS_PER_SECOND; millis %= MILLIS_PER_SECOND
        fun pad(value: Int, width: Int): String = if (value >= 0) value.toString().padStart(width, '0') else "-" + (-value.toLong()).toString().padStart(width - 1, '0')
        return "${pad(fields[0],4)}-${pad(fields[1]+1,2)}-${pad(fields[2],2)}T${pad(hour,2)}:${pad(minute,2)}:${pad(second,2)}.${pad(millis,3)}Z"
    }
}
