package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUResourceBundle
import kotlin.test.*

class ULocaleAvailabilityOracleTest {
    @Test fun publicLocaleListsAndBackedVersusCombinedViewsMatchOriginal() {
        assertEquals(com.ibm.icu.util.ULocale.getAvailableLocales().map { it.name }, ULocale.getAvailableLocales().map { it.getName() })
        for (type in ULocale.AvailableType.entries) {
            val j = com.ibm.icu.util.ULocale.getAvailableLocalesByType(com.ibm.icu.util.ULocale.AvailableType.valueOf(type.name))
            val k = ULocale.getAvailableLocalesByType(type)
            assertEquals(j.map { it.name }, k.map { it.getName() })
            assertFailsWith<UnsupportedOperationException> { (j as MutableList<com.ibm.icu.util.ULocale>).replaceAll { error("must not run") } }
            assertFailsWith<UnsupportedOperationException> { (k as MutableList<ULocale>).replaceAll { error("must not run") } }
        }
        val ja = com.ibm.icu.impl.ICUResourceBundle.getAvailableULocales()
        val ka = ICUResourceBundle.getAvailableULocales()!!
        val jDefault = com.ibm.icu.util.ULocale.getAvailableLocalesByType(com.ibm.icu.util.ULocale.AvailableType.DEFAULT).toList()
        val kDefault = ULocale.getAvailableLocalesByType(ULocale.AvailableType.DEFAULT) as List<ULocale>
        val jView = com.ibm.icu.util.ULocale.getAvailableLocalesByType(com.ibm.icu.util.ULocale.AvailableType.DEFAULT)
        val jCombined = com.ibm.icu.util.ULocale.getAvailableLocalesByType(com.ibm.icu.util.ULocale.AvailableType.WITH_LEGACY_ALIASES)
        val kCombined = ULocale.getAvailableLocalesByType(ULocale.AvailableType.WITH_LEGACY_ALIASES) as List<ULocale>
        val jSaved = ja[0]; val kSaved = ka[0]
        try {
            ja[0] = com.ibm.icu.util.ULocale("zz"); ka[0] = ULocale("zz")
            assertEquals(jView.first().name, kDefault[0].getName())
            assertEquals(jCombined.first().name, kCombined[0].getName())
            assertEquals(jDefault[0].name, kCombined[0].getName())
        } finally { ja[0] = jSaved; ka[0] = kSaved }
    }
}
