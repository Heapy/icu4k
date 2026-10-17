package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DayPeriodRulesTest {
    @Test fun englishBoundariesAndMidpoints() {
        val rules = DayPeriodRules.getInstance(ULocale("en_US"))!!
        assertTrue(rules.hasMidnight()); assertTrue(rules.hasNoon())
        assertEquals(DayPeriodRules.DayPeriod.MORNING1, rules.getDayPeriodForHour(0))
        assertEquals(DayPeriodRules.DayPeriod.MORNING1, rules.getDayPeriodForHour(6))
        assertEquals(DayPeriodRules.DayPeriod.AFTERNOON1, rules.getDayPeriodForHour(12))
        assertEquals(DayPeriodRules.DayPeriod.EVENING1, rules.getDayPeriodForHour(18))
        assertEquals(DayPeriodRules.DayPeriod.NIGHT1, rules.getDayPeriodForHour(21))
        assertEquals(22.5, rules.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.NIGHT1))
        assertEquals(0.0, rules.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.MIDNIGHT))
        assertEquals(12.0, rules.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.NOON))
        assertFailsWith<IllegalArgumentException> { rules.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.MORNING2) }
    }
    @Test fun russianNightWrapsAcrossMidnight() {
        val rules = DayPeriodRules.getInstance(ULocale("ru"))!!
        assertEquals(DayPeriodRules.DayPeriod.NIGHT1, rules.getDayPeriodForHour(22))
        assertEquals(DayPeriodRules.DayPeriod.NIGHT1, rules.getDayPeriodForHour(0))
        assertEquals(DayPeriodRules.DayPeriod.NIGHT1, rules.getDayPeriodForHour(3))
        assertEquals(DayPeriodRules.DayPeriod.MORNING1, rules.getDayPeriodForHour(4))
        assertEquals(1.0, rules.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.NIGHT1))
    }
    @Test fun fallbackIdentityAndUnsupportedLocale() {
        val rules = DayPeriodRules.getInstance(ULocale("en"))
        assertSame(rules, DayPeriodRules.getInstance(ULocale("en_US_POSIX@calendar=chinese")))
        assertNull(DayPeriodRules.getInstance(ULocale("zz_ZZ")))
        assertSame(DayPeriodRules.getInstance(ULocale.ROOT), DayPeriodRules.getInstance(ULocale("root")))
        assertFailsWith<IndexOutOfBoundsException> { rules!!.getDayPeriodForHour(24) }
        assertFailsWith<IndexOutOfBoundsException> { rules!!.getDayPeriodForHour(-1) }
    }
    @Test fun allEmbeddedLocaleRulesCoverEveryHour() {
        val locales = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "dayPeriods", disableFallback = true).get("locales")
        assertTrue(locales.getSize() > 80)
        for (i in 0 until locales.getSize()) {
            val id = locales.get(i).getKey()!!
            val rules = DayPeriodRules.getInstance(ULocale(id))!!
            for (hour in 0..23) assertNotNull(rules.getDayPeriodForHour(hour), "$id/$hour")
        }
        assertContentEquals(DayPeriodRules.DayPeriod.entries.toTypedArray(), DayPeriodRules.DayPeriod.VALUES)
    }
}
