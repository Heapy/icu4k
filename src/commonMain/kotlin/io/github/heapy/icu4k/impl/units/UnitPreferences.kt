// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Locale, measurement-system and usage selection from ICU's unitPreferenceData. */
internal class UnitPreferences {
    class UnitPreference(private val unit: String?, geq: String?, skeleton: String?) {
        private val geq = UnitDecimal(geq ?: "4.9E-324")
        private val skeleton = skeleton ?: ""
        fun getUnit(): String? = unit
        fun getGeq(): UnitDecimal = geq
        fun getSkeleton(): String = skeleton
    }
    class Sink : UResource.Sink() {
        val preferences = HashMap<String, HashMap<String, Array<UnitPreference>>>()
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val categories = value.getTable(); var i = 0
            while (categories.getKeyAndValue(i++, key, value)) {
                val category = key.toString()
                val usages = value.getTable(); var j = 0
                while (usages.getKeyAndValue(j++, key, value)) {
                    val usage = key.toString()
                    val regions = value.getTable(); var k = 0
                    while (regions.getKeyAndValue(k++, key, value)) {
                        val region = key.toString()
                        val array = value.getArray(); var l = 0
                        val entries = ArrayList<UnitPreference>()
                        while (array.getValue(l++, value)) {
                            val table = value.getTable(); var m = 0
                            var unit: String? = null; var geq = "1"; var skeleton = ""
                            while (table.getKeyAndValue(m++, key, value)) when (key.toString()) {
                                "unit" -> unit = value.getString()
                                "geq" -> geq = value.getString()
                                "skeleton" -> skeleton = value.getString()
                                else -> assertUnitPreference(false, "key must be unit, geq or skeleton")
                            }
                            assertUnitPreference(unit != null, null)
                            entries.add(UnitPreference(unit, geq, skeleton))
                        }
                        assertUnitPreference(entries.isNotEmpty(), null)
                        preferences.getOrPut(formMapKey(category, usage)) { HashMap() }[region] = entries.toTypedArray()
                    }
                }
            }
        }
    }
    private val preferences = Sink().also {
        (UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "units") as ICUResourceBundle)
            .getAllItemsWithFallback(UnitIdentifierData.Constants.UNIT_PREFERENCE_TABLE_NAME, it)
    }.preferences
    fun getPreferencesFor(category: String?, usage: String?, locale: ULocale?, data: UnitConversionData?): Array<UnitPreference>? {
        if (category == "temperature") {
            var localeUnit = locale!!.getKeywordValue("mu")
            if (localeUnit == "fahrenhe") localeUnit = "fahrenheit"
            val localeUnitCategory = try {
                if (localeUnit == null) null else data!!.getCategory(MeasureUnitImpl.forIdentifier(localeUnit)!!)
            } catch (_: Exception) { null }
            if (localeUnitCategory != null && category == localeUnitCategory) return arrayOf(UnitPreference(localeUnit, null, null))
        }
        @Suppress("DEPRECATION")
        val region = ULocale.getRegionForSupplementalData(locale, true)
        val system = locale!!.getKeywordValue("measure")
        val isSystem = measurementSystem.containsKey(system)
        var result: Array<UnitPreference>? = null
        for (subUsage in allUsages(usage!!)) {
            result = getUnitPreferences(category, subUsage, region)
            if (result != null && isSystem) {
                val rates = UnitConversionData()
                var unitsMatchSystem = true
                for (preference in result) for (unit in MeasureUnitImpl.forIdentifier(preference.getUnit())!!.getSingleUnits().toList()) {
                    val systems = rates.extractSystems(unit)!!
                    if (!systems.contains("metric_adjacent") && !systems.contains(system!!)) unitsMatchSystem = false
                }
                if (!unitsMatchSystem) result = getUnitPreferences(category, subUsage, measurementSystem[system]!!)
            }
            if (result != null) break
        }
        assertUnitPreference(result != null, "At least the category must be exist")
        return result
    }
    private fun getUnitPreferences(category: String?, usage: String?, region: String): Array<UnitPreference>? {
        val regional = preferences[formMapKey(category, usage)] ?: return null
        val result = if (regional.containsKey(region)) regional[region] else regional[UnitIdentifierData.Constants.DEFAULT_REGION]
        assertUnitPreference(result != null, null)
        return result
    }
    companion object {
        private val measurementSystem = mapOf("metric" to "001", "ussystem" to "US", "uksystem" to "GB")
        fun formMapKey(category: String?, usage: String?): String = "$category++$usage"
        private fun allUsages(usage: String): List<String> = buildList {
            add(usage)
            for (i in usage.lastIndex downTo 0) if (usage[i] == '-') add(usage.substring(0, i))
            if (usage != UnitIdentifierData.Constants.DEFAULT_USAGE) add(UnitIdentifierData.Constants.DEFAULT_USAGE)
        }
    }
}
internal expect fun assertUnitPreference(condition: Boolean, message: String?)
