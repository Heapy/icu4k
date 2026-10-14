package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UnitPreferencesTest {
    @Test fun temperatureKeywordOverridesRegionAndSystem() {
        val preferences = UnitPreferences(); val data = UnitConversionData()
        val locale = ULocale("en_US@mu=celsius;measure=ussystem")
        val first = preferences.getPreferencesFor("temperature", "default", locale, data)!!
        val second = preferences.getPreferencesFor("temperature", "default", locale, data)!!
        assertEquals("celsius", first.single().getUnit())
        assertEquals("4.9E-324", first.single().getGeq().toString())
        assertEquals("", first.single().getSkeleton())
        assertNotSame(first, second)
        assertEquals("fahrenheit", preferences.getPreferencesFor("temperature", "default", ULocale("fr@mu=fahrenhe"), data)!!.single().getUnit())
    }
    @Test fun usageFallbackReturnsTheCachedArray() {
        val preferences = UnitPreferences(); val data = UnitConversionData()
        val first = preferences.getPreferencesFor("length", "person-height", ULocale.US, data)!!
        assertSame(first, preferences.getPreferencesFor("length", "person-height-unlisted-child", ULocale.US, data))
        val metric = preferences.getPreferencesFor("length", "default", ULocale("en_US@measure=metric"), data)!!
        val world = preferences.getPreferencesFor("length", "default", ULocale("en_001"), data)!!
        assertContentEquals(world.map { it.getUnit() }, metric.map { it.getUnit() })
    }
    @Test fun factorComparatorsPlaceSpecialMappingsLast() {
        val comparator = MeasureUnitImpl.MeasureUnitImplComparator(UnitConversionData())
        val meter = MeasureUnitImpl.forIdentifier("meter")!!
        val foot = MeasureUnitImpl.forIdentifier("foot")!!
        val special = MeasureUnitImpl.forIdentifier("beaufort")!!
        assertTrue(comparator.compare(foot, meter) < 0)
        assertTrue(comparator.compare(meter, special) < 0)
        assertEquals(0, comparator.compare(special, special))
    }
}
