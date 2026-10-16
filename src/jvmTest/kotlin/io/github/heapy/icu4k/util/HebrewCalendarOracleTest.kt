@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*
import com.ibm.icu.util.Calendar as JCalendar
import com.ibm.icu.util.HebrewCalendar as JHebrew
import com.ibm.icu.util.SimpleTimeZone as JZone
import com.ibm.icu.util.ULocale as JLocale

class HebrewCalendarOracleTest {
    private fun compare(k: HebrewCalendar, j: JHebrew, label: String) {
        assertEquals(j.timeInMillis, k.getTimeInMillis(), label)
        for (field in 0..23) assertEquals(j.get(field), k.get(field), "$label/$field")
        assertEquals(j.temporalMonthCode, k.getTemporalMonthCode(), label)
        assertEquals(j.inTemporalLeapYear(), k.inTemporalLeapYear(), label)
        assertEquals(j.relatedYear, k.getRelatedYear(), label)
    }
    @Test fun fullMetonicCycleMatchesAllFieldsMonthLengthsAndOrdinalResolution() {
        for (offset in listOf(0, 19800000, -18000000)) {
            val k = HebrewCalendar(SimpleTimeZone(offset, "custom"), ULocale("he_IL"))
            val j = JHebrew(JZone(offset, "custom"), JLocale("he_IL"))
            for (year in 5770..5788) for (month in 0..12) {
                if (month == 5 && !JHebrew.isLeapYear(year)) continue
                k.clear(); j.clear(); k.set(year, month, 1); j.set(year, month, 1)
                compare(k, j, "$offset/$year/$month")
                for (field in listOf(Calendar.DATE, Calendar.DAY_OF_YEAR, Calendar.WEEK_OF_YEAR, Calendar.MONTH, Calendar.ORDINAL_MONTH)) {
                    assertEquals(j.getActualMaximum(field), k.getActualMaximum(field), "$year/$month/$field")
                }
                for (day in listOf(29, 30, 31, 0)) {
                    k.set(Calendar.DATE, day); j.set(JCalendar.DATE, day); compare(k, j, "$year/$month/$day")
                }
            }
            for (year in listOf(5784, 5785)) for (ordinal in -2..14) {
                k.clear(); j.clear(); k.set(Calendar.YEAR, year); j.set(JCalendar.YEAR, year)
                k.set(Calendar.ORDINAL_MONTH, ordinal); j.set(JCalendar.ORDINAL_MONTH, ordinal)
                k.set(Calendar.DATE, 1); j.set(JCalendar.DATE, 1)
                compare(k, j, "ordinal/$year/$ordinal")
            }
            for (field in 0..23) {
                assertEquals(j.getMinimum(field), k.getMinimum(field))
                assertEquals(j.getGreatestMinimum(field), k.getGreatestMinimum(field))
                assertEquals(j.getLeastMaximum(field), k.getLeastMaximum(field))
                assertEquals(j.getMaximum(field), k.getMaximum(field))
            }
        }
    }
    @Test fun addRollFieldStampPrecedenceAndCloneMatchSource() {
        for (locale in listOf("en_US", "he_IL", "en_GB@fw=mon")) {
            val k = HebrewCalendar(SimpleTimeZone(0, "custom"), ULocale(locale))
            val j = JHebrew(JZone(0, "custom"), JLocale(locale))
            for (year in listOf(5783, 5784, 5785)) for (month in listOf(0, 4, 6, 12)) {
                for (field in listOf(Calendar.MONTH, Calendar.ORDINAL_MONTH, Calendar.YEAR, Calendar.DATE, Calendar.WEEK_OF_YEAR)) {
                    for (amount in listOf(-25, -13, -1, 0, 1, 13, 25)) {
                        k.clear(); j.clear(); k.set(year, month, 30); j.set(year, month, 30)
                        k.add(field, amount); j.add(field, amount); compare(k, j, "add/$year/$month/$field/$amount")
                        k.roll(field, -amount); j.roll(field, -amount); compare(k, j, "roll/$year/$month/$field/$amount")
                    }
                }
            }
            k.clear(); j.clear(); k.set(5784, 5, 30); j.set(5784, 5, 30)
            val kc = k.clone() as HebrewCalendar; val jc = j.clone() as JHebrew
            kc.add(Calendar.MONTH, 1); jc.add(JCalendar.MONTH, 1)
            compare(kc, jc, "clone"); compare(k, j, "original")
            for (reverse in listOf(false, true)) {
                k.clear(); j.clear(); k.set(Calendar.YEAR, 5785); j.set(JCalendar.YEAR, 5785)
                val fields = if (reverse) listOf(Calendar.ORDINAL_MONTH, Calendar.MONTH) else listOf(Calendar.MONTH, Calendar.ORDINAL_MONTH)
                for (field in fields) { k.set(field, 7); j.set(field, 7) }
                compare(k, j, "stamps/$reverse")
            }
        }
    }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (error: Throwable) {
        if (error is VirtualMachineError) throw error
        "${error.javaClass.simpleName}:${error.message}"
    }
    @Test fun strictValidationAndTemporalFailurePolicyMatchSource() {
        val k = HebrewCalendar(SimpleTimeZone(0, "custom"), ULocale.US)
        val j = JHebrew(JZone(0, "custom"), JLocale.US)
        for (year in listOf(5784, 5785)) for (field in listOf(Calendar.MONTH, Calendar.ORDINAL_MONTH)) for (month in -1..13) {
            k.clear(); j.clear(); k.setLenient(false); j.isLenient = false
            k.set(Calendar.YEAR, year); j.set(JCalendar.YEAR, year)
            k.set(field, month); j.set(field, month); k.set(Calendar.DATE, 30); j.set(JCalendar.DATE, 30)
            assertEquals(outcome { j.timeInMillis }, outcome { k.getTimeInMillis() }, "$year/$field/$month")
        }
        for (code in listOf(null, "", "M00", "M01", "M05L", "M06L", "M12", "M13", "m01")) {
            k.clear(); j.clear()
            // Null exception messages depend on the host VM; compare the exception class.
            assertEquals(outcome { j.setTemporalMonthCode(code) }.substringBefore(':'), outcome { k.setTemporalMonthCode(code) }.substringBefore(':'), "$code")
        }
        for (year in listOf(Int.MIN_VALUE, -5000000, -19, -1, 0, 1, 5784, 5785, 5000000, Int.MAX_VALUE)) {
            assertEquals(JHebrew.isLeapYear(year), HebrewCalendar.isLeapYear(year), "$year")
        }
    }
}
