package io.github.heapy.icu4k.util

import kotlin.test.*
import com.ibm.icu.util.Calendar as JCalendar
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.SimpleTimeZone as JZone
import com.ibm.icu.util.CopticCalendar as JCoptic
import com.ibm.icu.util.EthiopicCalendar as JEthiopic

class CECalendarOracleTest {
    @Test fun fieldsMutationsAndLimitsMatchPinnedSource() {
        for (locale in listOf("en_US", "am_ET", "am_ET@calendar=ethiopic-amete-alem")) {
            for (offset in listOf(0, 19800000)) {
                for (kind in 0..1) {
                    val k: Calendar = if (kind == 0) CopticCalendar(SimpleTimeZone(offset, "custom"), ULocale(locale)) else EthiopicCalendar(SimpleTimeZone(offset, "custom"), ULocale(locale))
                    val j: JCalendar = if (kind == 0) JCoptic(JZone(offset, "custom"), JLocale(locale)) else JEthiopic(JZone(offset, "custom"), JLocale(locale))
                    fun compare() {
                        assertEquals(j.type, k.getType())
                        assertEquals(j.timeInMillis, k.getTimeInMillis())
                        for (field in 0..23) assertEquals(j.get(field), k.get(field), "$locale/$kind/$field")
                    }
                    for (time in listOf(-62135596800000L, -2208988800000L, -1L, 0L, 1726012800000L, 253402300799999L)) {
                        k.setTimeInMillis(time); j.timeInMillis = time; compare()
                        for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE)) {
                            k.add(field, 17); j.add(field, 17); compare()
                            k.roll(field, -5); j.roll(field, -5); compare()
                        }
                        for (field in 0..23) {
                            assertEquals(j.getMinimum(field), k.getMinimum(field))
                            assertEquals(j.getMaximum(field), k.getMaximum(field))
                            assertEquals(j.getActualMaximum(field), k.getActualMaximum(field))
                        }
                        k.setTemporalMonthCode("M13"); j.setTemporalMonthCode("M13"); compare()
                        assertEquals(j.temporalMonthCode, k.getTemporalMonthCode())
                    }
                    for (year in listOf(-5501, -1, 0, 1, 3, 4, 1739, 2015, 7515)) {
                        k.clear(); j.clear(); k.set(Calendar.EXTENDED_YEAR, year); j.set(JCalendar.EXTENDED_YEAR, year)
                        k.set(Calendar.MONTH, 12); j.set(JCalendar.MONTH, 12)
                        k.set(Calendar.DATE, 5); j.set(JCalendar.DATE, 5); compare()
                    }
                }
            }
        }
    }
    @Test fun eraSwitchPreservesCachedFieldsAndCloneRebindsOwner() {
        for (mode in listOf(false, true)) {
            val k = EthiopicCalendar(SimpleTimeZone(0, "custom"), ULocale.US)
            val j = JEthiopic(JZone(0, "custom"), JLocale.US)
            k.setTimeInMillis(0); j.timeInMillis = 0
            assertEquals(j.get(JCalendar.YEAR), k.get(Calendar.YEAR))
            k.setAmeteAlemEra(mode); j.setAmeteAlemEra(mode)
            assertEquals(j.get(JCalendar.YEAR), k.get(Calendar.YEAR))
            val kc = k.clone() as EthiopicCalendar; val jc = j.clone() as JEthiopic
            kc.setAmeteAlemEra(!mode); jc.setAmeteAlemEra(!mode)
            kc.clear(); jc.clear(); kc.set(7500, 12, 5); jc.set(7500, 12, 5)
            assertEquals(jc.timeInMillis, kc.getTimeInMillis())
            assertEquals(mode, k.isAmeteAlemEra()); assertEquals(!mode, kc.isAmeteAlemEra())
            assertEquals(j.type, k.getType()); assertEquals(jc.type, kc.getType())
        }
    }
    @Test fun staticConversionsMatchIncludingOverflowAndNegativeMonths() {
        for (year in listOf(Long.MIN_VALUE, -5500L, -1L, 0L, 1L, 2024L, Long.MAX_VALUE)) {
            for (month in listOf(Int.MIN_VALUE, -27, -13, -1, 0, 12, 13, 27, Int.MAX_VALUE)) {
                assertEquals(JCoptic.copticToJD(year, month, 7), CopticCalendar.copticToJD(year, month, 7))
                assertEquals(JEthiopic.EthiopicToJD(year, month, 7), EthiopicCalendar.EthiopicToJD(year, month, 7))
            }
        }
    }
}
