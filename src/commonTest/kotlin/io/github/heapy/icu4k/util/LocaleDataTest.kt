@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class LocaleDataTest {
    @Test fun exemplarsDelimitersAndPatternsUsePinnedBundles() {
        val en = LocaleData.getInstance(ULocale.ENGLISH)
        assertFalse(en.getNoSubstitute())
        assertTrue(en.getExemplarSet(0, LocaleData.ES_STANDARD)!!.contains('a'.code))
        assertTrue(LocaleData.getExemplarSet(ULocale.ENGLISH, UnicodeSet.CASE_INSENSITIVE)!!.contains('A'.code))
        assertEquals("“", en.getDelimiter(LocaleData.QUOTATION_START))
        assertEquals("”", en.getDelimiter(LocaleData.QUOTATION_END))
        assertTrue(en.getLocaleDisplayPattern().contains("{0}"))
        assertEquals(", ", en.getLocaleSeparator())
        assertSame(UnicodeSet.EMPTY, en.getExemplarSet(0, LocaleData.ES_CURRENCY))
        en.setNoSubstitute(true)
        assertNull(en.getExemplarSet(0, LocaleData.ES_CURRENCY))
        assertFailsWith<IllegalArgumentException> { en.getExemplarSet(0, -1) }
        assertFailsWith<IndexOutOfBoundsException> { en.getDelimiter(4) }
        assertTrue(UnicodeSet.EMPTY.isFrozen())
        assertTrue(UnicodeSet.ALL_CODE_POINTS.isFrozen())
        assertEquals(0x110000, UnicodeSet.ALL_CODE_POINTS.size())
    }
    @Test fun turkicBoundsAndRegionValidatorOverrides() {
        val general = ULocale.getDefault()
        val display = ULocale.getDefault(ULocale.Category.DISPLAY)
        val format = ULocale.getDefault(ULocale.Category.FORMAT)
        try {
            ULocale.setDefault(ULocale("tr_TR"))
            assertEquals("", ULocale.getRegionForSupplementalData(ULocale("en@rg=idzzzz"), false))
            assertFailsWith<IndexOutOfBoundsException> { ULocale.RegionValidateMap().isSet("ID") }
            assertFailsWith<IndexOutOfBoundsException> { ULocale.getRegionForSupplementalData(ULocale("en@rg=IDzzzz"), false) }
        } finally {
            ULocale.setDefault(general)
            ULocale.setDefault(ULocale.Category.DISPLAY, display)
            ULocale.setDefault(ULocale.Category.FORMAT, format)
        }
        val override = object : ULocale.RegionValidateMap() { override fun value(region: String?): Int = -1 }
        assertFalse(override.isSet(null))
        val nullMap = object : ULocale.RegionValidateMap() { init { map = null } }
        assertTrue(nullMap.equals(object : ULocale.RegionValidateMap() { init { map = null } }))
        assertFalse(nullMap.equals(ULocale.RegionValidateMap()))
    }
    @Test fun measurementRegionAndPaperRules() {
        assertSame(LocaleData.MeasurementSystem.US, LocaleData.getMeasurementSystem(ULocale.US))
        assertSame(LocaleData.MeasurementSystem.UK, LocaleData.getMeasurementSystem(ULocale.UK))
        assertSame(LocaleData.MeasurementSystem.SI, LocaleData.getMeasurementSystem(ULocale.FRANCE))
        assertEquals(listOf(279, 216), LocaleData.getPaperSize(ULocale.US).let { listOf(it.getHeight(), it.getWidth()) })
        assertEquals(listOf(297, 210), LocaleData.getPaperSize(ULocale.FRANCE).let { listOf(it.getHeight(), it.getWidth()) })
        assertEquals("GB", ULocale.getRegionForSupplementalData(ULocale("en_US@rg=gbzzzz"), false))
        assertEquals("US", ULocale.getRegionForSupplementalData(ULocale("en@sd=usca"), true))
        assertEquals("", ULocale.getRegionForSupplementalData(ULocale("en@sd=usca"), false))
        assertEquals("FR", ULocale.getRegionForSupplementalData(ULocale("fr"), true))
        assertFailsWith<NullPointerException> { LocaleData.getMeasurementSystem(null) }
        assertSame(LocaleData.getCLDRVersion(), LocaleData.getCLDRVersion())
    }
}
