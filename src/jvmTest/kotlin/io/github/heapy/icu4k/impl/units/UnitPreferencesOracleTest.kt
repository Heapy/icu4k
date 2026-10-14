package com.ibm.icu.impl.units

import io.github.heapy.icu4k.impl.units.UnitPreferences as KPreferences
import io.github.heapy.icu4k.impl.units.UnitConversionData
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl as KUnit
import io.github.heapy.icu4k.util.ULocale as KLocale
import com.ibm.icu.util.ULocale as JLocale
import kotlin.test.*

class UnitPreferencesOracleTest {
    private fun snapshot(values: Array<UnitPreferences.UnitPreference>?): List<List<String?>>? = values?.map { listOf(it.unit, it.geq.toString(), it.skeleton) }
    private fun snapshot(values: Array<KPreferences.UnitPreference>?): List<List<String?>>? = values?.map { listOf(it.getUnit(), it.getGeq().toString(), it.getSkeleton()) }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { listOf(e.javaClass.simpleName, e.message) }
    @Test fun entirePreferenceResourceMatchesSource() {
        val source = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "units") as com.ibm.icu.impl.ICUResourceBundle
        val js = UnitPreferences.UnitPreferencesSink(); source.getAllItemsWithFallback("unitPreferenceData", js)
        val resource = io.github.heapy.icu4k.util.UResourceBundle.getBundleInstance(io.github.heapy.icu4k.impl.ICUData.ICU_BASE_NAME, "units") as io.github.heapy.icu4k.impl.ICUResourceBundle
        val ks = KPreferences.Sink(); resource.getAllItemsWithFallback("unitPreferenceData", ks)
        assertEquals(js.mapToUnitPreferences.keys, ks.preferences.keys)
        for ((usage, regions) in js.mapToUnitPreferences) {
            assertEquals(regions.keys, ks.preferences[usage]!!.keys, usage)
            for ((region, values) in regions) assertEquals(snapshot(values), snapshot(ks.preferences[usage]!![region]), "$usage/$region")
        }
    }
    @Test fun localeAndUsageFallbacksMatchSource() {
        val j = UnitPreferences(); val k = KPreferences(); val jd = UnitsData(); val kd = UnitConversionData()
        val source = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "units") as com.ibm.icu.impl.ICUResourceBundle
        val sink = UnitPreferences.UnitPreferencesSink(); source.getAllItemsWithFallback("unitPreferenceData", sink)
        val locales = listOf("en_US", "en_GB", "fr_FR", "ja_JP", "en_AU", "und", "en_US@rg=gbzzzz", "en_US@measure=metric", "fr_FR@measure=ussystem", "en_US@measure=uksystem", "en@measure=unknown")
        for (key in sink.mapToUnitPreferences.keys) {
            val (category, usage) = key.split("++", limit = 2)
            for (suffix in listOf("", "-unlisted-child")) for (locale in locales) assertEquals(
                outcome { snapshot(j.getPreferencesFor(category, usage + suffix, JLocale(locale), jd)) },
                outcome { snapshot(k.getPreferencesFor(category, usage + suffix, KLocale(locale), kd)) }, "$key$suffix/$locale")
        }
        for (unit in listOf("fahrenhe", "celsius", "kelvin", "meter", "bad", "square-celsius", "milligram-per-deciliter")) for (usage in listOf("default", "weather", "unlisted")) {
            val locale = "en_US@mu=$unit;measure=metric"
            assertEquals(outcome { snapshot(j.getPreferencesFor("temperature", usage, JLocale(locale), jd)) }, outcome { snapshot(k.getPreferencesFor("temperature", usage, KLocale(locale), kd)) }, "$unit/$usage")
        }
        assertEquals(outcome { j.getPreferencesFor("unknown", "default", JLocale.US, jd) }, outcome { k.getPreferencesFor("unknown", "default", KLocale.US, kd) })
        assertEquals(UnitPreferences.UnitPreference(null, null, null).geq.toString(), KPreferences.UnitPreference(null, null, null).getGeq().toString())
    }
    @Test fun conversionComparatorsMatchSource() {
        val j = MeasureUnitImpl.MeasureUnitImplComparator(ConversionRates())
        val k = KUnit.MeasureUnitImplComparator(UnitConversionData())
        val ids = listOf("meter", "foot", "inch", "mile", "kilometer", "per-meter", "square-meter", "beaufort", "mile-per-hour", "meter-per-second", "degree", "radian", "celsius")
        for (a in ids) for (b in ids) assertEquals(j.compare(MeasureUnitImpl.forIdentifier(a), MeasureUnitImpl.forIdentifier(b)).coerceIn(-1, 1), k.compare(KUnit.forIdentifier(a)!!, KUnit.forIdentifier(b)!!).coerceIn(-1, 1), "$a/$b")
        val ji = MeasureUnitImpl.MeasureUnitImplWithIndexComparator(ConversionRates())
        val ki = KUnit.MeasureUnitImplWithIndexComparator(UnitConversionData())
        val ja = MeasureUnitImpl.forIdentifier("meter-and-centimeter").extractIndividualUnitsWithIndices()
        val ka = KUnit.forIdentifier("meter-and-centimeter")!!.extractIndividualUnitsWithIndices()
        assertEquals(ji.compare(ja[0], ja[1]), ki.compare(ka[0], ka[1]))
    }
}
