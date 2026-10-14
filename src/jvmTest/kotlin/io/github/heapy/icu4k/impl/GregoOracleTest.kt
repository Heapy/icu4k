package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.Grego as Original

class GregoOracleTest {
    @Test fun arithmeticFormattingAndArrayReuseMatch() {
        val random = Random(135)
        val times = listOf(Long.MIN_VALUE, Long.MAX_VALUE, Grego.MIN_MILLIS, Grego.MAX_MILLIS, -1, 0, 1) + List(600) { random.nextLong() }
        for (time in times) {
            assertContentEquals(Original.timeToFields(time, null), Grego.timeToFields(time, null))
            assertEquals(Original.timeToYear(time), Grego.timeToYear(time))
            assertEquals(Original.timeToString(time), Grego.timeToString(time))
            assertEquals(Original.dayOfWeek(time), Grego.dayOfWeek(time))
            val year = Original.dayToYear(time)
            assertEquals(year.first to year.second, Grego.dayToYear(time))
            assertContentEquals(Original.dayToFields(time, null), Grego.dayToFields(time, null))
        }
        for (year in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -400, 0, 1, 1900, 1970, 2000) + List(300) { random.nextInt() }) {
            assertEquals(Original.isLeapYear(year), Grego.isLeapYear(year))
            for (month in 0..11) {
                assertEquals(Original.monthLength(year, month), Grego.monthLength(year, month))
                assertEquals(Original.previousMonthLength(year, month), Grego.previousMonthLength(year, month))
                val dom = random.nextInt(-4, 40)
                assertEquals(Original.fieldsToDay(year, month, dom), Grego.fieldsToDay(year, month, dom))
                assertEquals(Original.getDayOfWeekInMonth(year, month, dom), Grego.getDayOfWeekInMonth(year, month, dom))
            }
        }
        for (size in 0..9) {
            val ja = IntArray(size) { 47 }; val ka = ja.copyOf()
            val j = Original.timeToFields(-1, ja); val k = Grego.timeToFields(-1, ka)
            assertContentEquals(j, k); assertContentEquals(ja, ka); assertEquals(j === ja, k === ka)
        }
        for (n in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -2, -1, 0, 1, 2)) for (d in listOf(-100L, -1, 1, 2, 7, Long.MAX_VALUE))
            assertEquals(Original.floorDivide(n, d), Grego.floorDivide(n, d))
    }
}
